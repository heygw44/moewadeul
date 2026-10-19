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
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.math.BigDecimal;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 하루 비용 한도에 거의 닿았을 때 요청이 한꺼번에 몰리면 한도를 얼마나 넘는지 확인한다.
 *
 * <p>서버는 OpenAI를 부르기 전에 오늘 합계를 잠금 없이 읽어 판정하고, 응답을 받은 뒤에 토큰을 더한다. 그래서 응답을 기다리는 사이에 들어온
 * 요청은 모두 판정을 통과한다. 정책은 이 넘침을 "마지막 몇 건만큼"으로 허용하므로, 이 테스트는 넘침이 막 진행 중이던 호출 수만큼으로
 * 묶이고 몰린 요청이 끝난 뒤에는 다음 요청을 막는지를 확인한다.
 */
@IntegrationTest
class AiBudgetBurstIntegrationTest {

    private static final String PATH = "/openai/v1/chat/completions";
    private static final String SENTENCE = "강원도 계곡 근처 야영장";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final int THREADS = 50;
    private static final int OPENAI_DELAY_MILLIS = 1_000;
    // 호출 한 번의 토큰 수는 기술 결정 때 실제 지시문과 답변 형식으로 추정한 값(입력 약 1,500, 출력 약 200)을 쓴다.
    // 기본 단가로 한 번에 0.00092달러다.
    private static final int INPUT_PER_CALL = 1_500;
    private static final int OUTPUT_PER_CALL = 200;
    // 다른 회원이 오늘 이미 0.99954달러를 써서, 남은 금액(0.00046달러)이 호출 반 번 값이다. 그래서 한 번만 불러도 한도를 넘는다.
    private static final int ALREADY_USED_INPUT_TOKENS = 2_498_850;

    @Autowired
    private AiInterpretService service;

    @Autowired
    private AiSearchUsageRecorder usageRecorder;

    @Autowired
    private AiSearchProperties properties;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("[F-27][AI-04] 한도에 거의 닿았을 때 50건이 몰리면, 넘친 금액은 OpenAI까지 간 호출 수만큼이고 그 뒤 요청은 OpenAI를 부르지 않는다")
    void burstOverrunIsBoundedByInFlightCalls() throws Exception {
        // given
        insertUsage(saveMember(), ALREADY_USED_INPUT_TOKENS);
        assertThat(properties.isBudgetExhausted(usageRecorder.sumTokens(TODAY))).isFalse();
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            members.add(saveMember());
        }
        stubOkAfterDelay();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (long memberId : members) {
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
        int calls = wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH))).size();
        BigDecimal overrun = properties.costUsd(usageRecorder.sumTokens(TODAY)).subtract(properties.dailyBudgetUsd());
        BigDecimal costPerCall = properties.costUsd(new TokenTotals(INPUT_PER_CALL, OUTPUT_PER_CALL));
        assertThat(calls).isBetween(1, THREADS);
        assertThat(outcomes.stream().filter("OK"::equals).count()).isEqualTo(calls);
        assertThat(overrun).isPositive().isLessThanOrEqualTo(costPerCall.multiply(BigDecimal.valueOf(calls)));

        BusinessException after =
                catchThrowableOfType(BusinessException.class, () -> service.interpret(saveMember(), SENTENCE));
        assertThat(after.getErrorCode()).isEqualTo(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE);
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)))).hasSize(calls);
    }

    private String outcomeOf(long memberId) {
        try {
            service.interpret(memberId, SENTENCE);
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        }
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private void insertUsage(long memberId, int inputTokens) {
        jdbc.update(
                "INSERT INTO ai_search_usage (member_id, usage_date, request_count, input_tokens, output_tokens,"
                        + " updated_at) VALUES (?, ?, 1, ?, 0, NOW(6))",
                memberId,
                TODAY,
                inputTokens);
    }

    private void stubOkAfterDelay() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("region", "강원도");
        content.put("radiusKm", null);
        content.put("types", List.of("CAMPSITE"));
        content.put("hasWater", false);
        content.put("hasToilet", false);
        content.put("excludeWarning", false);
        content.put("hideClosed", false);
        content.put("settings", List.of("VALLEY"));
        content.put("unparsed", List.of());
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
        body.put("usage", Map.of("prompt_tokens", INPUT_PER_CALL, "completion_tokens", OUTPUT_PER_CALL));
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(OPENAI_DELAY_MILLIS)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(body))));
    }
}
