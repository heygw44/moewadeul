package com.moewadeul.aisearch.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
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

@IntegrationTest
class AiBudgetBurstIntegrationTest {

    private static final String PATH = "/openai/v1/chat/completions";
    private static final String SENTENCE = "강원도 계곡 근처 야영장";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final int THREADS = 50;
    private static final int OPENAI_DELAY_MILLIS = 1_000;
    private static final int INPUT_PER_CALL = 1_500;
    private static final int OUTPUT_PER_CALL = 200;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private AiInterpretService service;

    @Autowired
    private AiSearchUsageRecorder usageRecorder;

    @Autowired
    private AiSearchProperties properties;

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
        insertUsage(memberFixture.save().getId(), inputTokensLeavingHalfACallOfBudget());
        assertThat(properties.isBudgetExhausted(usageRecorder.sumTokens(TODAY))).isFalse();
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            members.add(memberFixture.save().getId());
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

        BusinessException after = catchThrowableOfType(
                BusinessException.class,
                () -> service.interpret(memberFixture.save().getId(), SENTENCE));
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

    private int inputTokensLeavingHalfACallOfBudget() {
        BigDecimal halfCall = properties
                .costUsd(new TokenTotals(INPUT_PER_CALL, OUTPUT_PER_CALL))
                .divide(BigDecimal.TWO);
        return properties
                .dailyBudgetUsd()
                .subtract(halfCall)
                .multiply(BigDecimal.valueOf(1_000_000))
                .divide(properties.inputPricePerMillionUsd())
                .intValueExact();
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
