package com.moewadeul.aisearch.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "moewadeul.external-api.max-concurrent-calls=100")
class AiInterpretServiceIntegrationTest {

    private static final String PATH = "/openai/v1/chat/completions";
    private static final String SENTENCE = "  쿠쿠다스봉우리 근처 계곡 야영장 식수 있는 곳  ";
    private static final String SECRET_PART = "쿠쿠다스봉우리";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private AiInterpretService service;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("[F-27][AI-01] 성공하면 읽어 낸 조건을 돌려주고, 사용 행에 요청 1건과 토큰 수를 남기며, 문장은 앞뒤 공백을 뺀 채 OpenAI에 보낸다")
    void successRecordsRequestAndTokens() {
        // given
        long memberId = saveMember();
        Map<String, Object> content = content();
        content.put("region", "강원도");
        content.put("hasWater", true);
        stubOk(content, 321, 45);

        // when
        SpotQueryConditions result = service.interpret(memberId, SENTENCE);

        // then
        assertThat(result.region()).isEqualTo("강원도");
        assertThat(result.hasWater()).isTrue();
        assertThat(usage(memberId, TODAY)).containsExactly(1, 321, 45);
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)))
                        .getFirst()
                        .getBodyAsString())
                .contains("\"content\":\"쿠쿠다스봉우리 근처 계곡 야영장 식수 있는 곳\"");
    }

    @Test
    @DisplayName("[F-27][AI-05] OpenAI가 500을 응답하면 503 AI_SEARCH_UNAVAILABLE이고, 요청은 1건 세고 토큰은 더하지 않는다")
    void openAiFailureCountsRequestButNoTokens() {
        // given
        long memberId = saveMember();
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        // when
        BusinessException e =
                catchThrowableOfType(BusinessException.class, () -> service.interpret(memberId, SENTENCE));

        // then
        assertThat(e.getErrorCode()).isEqualTo(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE);
        assertThat(usage(memberId, TODAY)).containsExactly(1, 0, 0);
    }

    @Test
    @DisplayName("[F-27][AI-05] 응답은 왔지만 내용이 스키마에 맞지 않으면 503이고, 쓴 토큰은 더한다")
    void schemaMismatchAddsTokens() {
        // given
        long memberId = saveMember();
        Map<String, Object> content = content();
        content.put("types", List.of("HOTEL"));
        stubOk(content, 200, 20);

        // when
        BusinessException e =
                catchThrowableOfType(BusinessException.class, () -> service.interpret(memberId, SENTENCE));

        // then
        assertThat(e.getErrorCode()).isEqualTo(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE);
        assertThat(usage(memberId, TODAY)).containsExactly(1, 200, 20);
    }

    @Test
    @DisplayName(
            "[F-27][AI-03] 21번째 요청은 429 AI_SEARCH_LIMITED이고 OpenAI를 부르지 않으며, Retry-After는 다음 날 0시(한국 시간)까지 남은 시간이다")
    void twentyFirstRequestIsLimited() {
        // given
        long memberId = saveMember();
        stubOk(content(), 10, 5);
        for (int i = 0; i < 20; i++) {
            service.interpret(memberId, SENTENCE);
        }

        // when
        RateLimitedException e =
                catchThrowableOfType(RateLimitedException.class, () -> service.interpret(memberId, SENTENCE));

        // then
        assertThat(e.getErrorCode()).isEqualTo(AiSearchErrorCode.AI_SEARCH_LIMITED);
        assertThat(e.retryAfter()).isEqualTo(Duration.ofHours(12));
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)))).hasSize(20);
        assertThat(usage(memberId, TODAY)).containsExactly(20, 200, 100);
    }

    @Test
    @DisplayName("[F-27][AI-03] 한국 날짜가 바뀌면 횟수를 다시 센다")
    void limitResetsOnNextKoreanDay() {
        // given
        long memberId = saveMember();
        stubOk(content(), 10, 5);
        for (int i = 0; i < 20; i++) {
            service.interpret(memberId, SENTENCE);
        }
        clock.advance(Duration.ofHours(13));

        // when
        SpotQueryConditions result = service.interpret(memberId, SENTENCE);

        // then
        assertThat(result.unparsed()).isEmpty();
        assertThat(usage(memberId, LocalDate.of(2026, 10, 6))).containsExactly(1, 10, 5);
        assertThat(usage(memberId, TODAY)).containsExactly(20, 200, 100);
    }

    @Test
    @DisplayName("[F-27][AI-03] 한도는 회원마다 따로 센다")
    void limitIsPerMember() {
        // given
        long first = saveMember();
        long second = saveMember();
        stubOk(content(), 10, 5);
        for (int i = 0; i < 20; i++) {
            service.interpret(first, SENTENCE);
        }

        // when
        SpotQueryConditions result = service.interpret(second, SENTENCE);

        // then
        assertThat(result).isNotNull();
        assertThat(usage(second, TODAY)).containsExactly(1, 10, 5);
    }

    @Test
    @DisplayName("[F-27][AI-04] 오늘 서비스 전체 비용이 1달러에 닿았으면 503이고 OpenAI를 부르지 않으며, 그 요청은 회원의 하루 횟수에서 빼지 않는다")
    void budgetExceededBlocksOpenAiCall() {
        // given
        long other = saveMember();
        long caller = saveMember();
        insertUsage(other, TODAY, 1, 2_500_000, 0);
        insertUsage(caller, TODAY, 3, 0, 0);
        stubOk(content(), 10, 5);
        long budgetBefore = counter("budget_exceeded");

        // when
        BusinessException e = catchThrowableOfType(BusinessException.class, () -> service.interpret(caller, SENTENCE));

        // then
        assertThat(e.getErrorCode()).isEqualTo(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE);
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)))).isEmpty();
        assertThat(usage(caller, TODAY)).containsExactly(3, 0, 0);
        assertThat(counter("budget_exceeded") - budgetBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27][AI-04] 어제 쓴 토큰은 오늘 비용 한도에 들어가지 않는다")
    void yesterdayTokensDoNotCountTowardToday() {
        // given
        long other = saveMember();
        long caller = saveMember();
        insertUsage(other, TODAY.minusDays(1), 1, 5_000_000, 5_000_000);
        stubOk(content(), 10, 5);

        // when
        SpotQueryConditions result = service.interpret(caller, SENTENCE);

        // then
        assertThat(result).isNotNull();
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)))).hasSize(1);
    }

    @Test
    @DisplayName("[F-27][AI-03] 같은 회원이 30개 스레드로 동시에 요청해도 20건만 성공하고 10건은 AI_SEARCH_LIMITED다")
    void concurrentRequestsRespectDailyLimit() throws Exception {
        // given
        long memberId = saveMember();
        stubOk(content(), 10, 5);
        int threads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return outcomeOf(memberId);
            }));
        }

        // when
        ready.await();
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(outcomes.stream().filter("OK"::equals).count()).isEqualTo(20);
        assertThat(outcomes.stream().filter("AI_SEARCH_LIMITED"::equals).count())
                .isEqualTo(10);
        assertThat(usage(memberId, TODAY)).containsExactly(20, 200, 100);
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)))).hasSize(20);
    }

    @Test
    @DisplayName("[F-27][AI-06] 성공하든 실패하든 문장은 로그와 DB에 남지 않는다")
    void sentenceIsNeverLoggedOrStored(CapturedOutput output) {
        // given
        long memberId = saveMember();
        stubOk(content(), 10, 5);
        service.interpret(memberId, SENTENCE);
        wireMock.resetAll();
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
        catchThrowableOfType(BusinessException.class, () -> service.interpret(memberId, SENTENCE));
        wireMock.resetAll();
        Map<String, Object> bad = content();
        bad.put("types", List.of("HOTEL"));
        stubOk(bad, 10, 5);
        catchThrowableOfType(BusinessException.class, () -> service.interpret(memberId, SENTENCE));

        // when
        String stored = jdbc.queryForList("SELECT * FROM ai_search_usage").toString();

        // then
        assertThat(output.getAll()).doesNotContain(SECRET_PART);
        assertThat(stored).doesNotContain(SECRET_PART);
        assertThat(meterRegistry.getMeters().stream()
                        .map(meter -> meter.getId().toString())
                        .toList())
                .noneMatch(id -> id.contains(SECRET_PART));
    }

    @Test
    @DisplayName("[F-27][AI-08] 결과를 지표에 나눠 세고, 반영하지 못한 조건이 있으면 unparsed 지표를 올린다")
    void recordsOutcomeAndUnparsedMetrics() {
        // given
        long memberId = saveMember();
        Map<String, Object> content = content();
        content.put("unparsed", List.of("조용한"));
        stubOk(content, 10, 5);
        long successBefore = counter("success");
        double unparsedBefore =
                meterRegistry.counter("moewadeul.aisearch.unparsed").count();

        // when
        service.interpret(memberId, SENTENCE);

        // then
        assertThat(counter("success") - successBefore).isEqualTo(1);
        assertThat(meterRegistry.counter("moewadeul.aisearch.unparsed").count() - unparsedBefore)
                .isEqualTo(1.0);
    }

    private String outcomeOf(long memberId) {
        try {
            service.interpret(memberId, SENTENCE);
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        }
    }

    private long counter(String outcome) {
        return (long) meterRegistry
                .counter("moewadeul.aisearch.requests", "outcome", outcome)
                .count();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private void insertUsage(long memberId, LocalDate date, int requests, int inputTokens, int outputTokens) {
        jdbc.update(
                "INSERT INTO ai_search_usage (member_id, usage_date, request_count, input_tokens, output_tokens,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, NOW(6))",
                memberId,
                date,
                requests,
                inputTokens,
                outputTokens);
    }

    private List<Integer> usage(long memberId, LocalDate date) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT request_count, input_tokens, output_tokens FROM ai_search_usage"
                        + " WHERE member_id = ? AND usage_date = ?",
                memberId,
                date);
        return List.of(
                ((Number) row.get("request_count")).intValue(),
                ((Number) row.get("input_tokens")).intValue(),
                ((Number) row.get("output_tokens")).intValue());
    }

    private Map<String, Object> content() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("region", null);
        content.put("radiusKm", null);
        content.put("types", List.of());
        content.put("hasWater", false);
        content.put("hasToilet", false);
        content.put("excludeWarning", false);
        content.put("hideClosed", false);
        content.put("settings", List.of());
        content.put("unparsed", List.of());
        return content;
    }

    private void stubOk(Map<String, Object> content, int input, int output) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", jsonMapper.writeValueAsString(content));
        message.put("refusal", null);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("choices", List.of(choice));
        body.put("usage", Map.of("prompt_tokens", input, "completion_tokens", output));
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(body))));
    }
}
