package com.moewadeul.aisearch.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.moewadeul.aisearch.application.AiSpotSetting;
import com.moewadeul.aisearch.application.AiSpotType;
import com.moewadeul.aisearch.application.SpotQueryInterpretation;
import com.moewadeul.aisearch.application.SpotQueryInterpretationException;
import com.moewadeul.aisearch.application.TokenUsage;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.testsupport.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OpenAiSpotQueryInterpreterIntegrationTest {

    private static final String PATH = "/openai/v1/chat/completions";
    private static final String REQUEST_TIMER = "moewadeul.external.api.requests";
    private static final String SENTENCE = "쿠쿠다스봉우리 근처 계곡 야영장 식수 있는 곳 주말에";

    @Autowired
    private OpenAiSpotQueryInterpreter interpreter;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ExternalApiMetrics metrics;

    @Test
    @DisplayName("[F-27] 응답의 조건을 모두 읽어 해석 결과와 토큰 수로 돌려준다")
    void mapsResponseToConditions() {
        // given
        Map<String, Object> content = content();
        content.put("region", "설악산");
        content.put("types", List.of("CAMPSITE", "BAKJI"));
        content.put("hasWater", true);
        content.put("hasToilet", true);
        content.put("excludeWarning", true);
        content.put("hideClosed", true);
        content.put("settings", List.of("VALLEY", "MOUNTAIN"));
        content.put("unparsed", List.of("주말", "조용한"));
        content.put("radiusKm", 10);
        stubOk(content, 321, 45);

        // when
        SpotQueryInterpretation result = interpreter.interpret(SENTENCE);

        // then
        assertThat(result.conditions().region()).isEqualTo("설악산");
        assertThat(result.conditions().radiusKm()).isEqualTo(10.0);
        assertThat(result.conditions().types()).containsExactlyInAnyOrder(AiSpotType.CAMPSITE, AiSpotType.BAKJI);
        assertThat(result.conditions().settings())
                .containsExactlyInAnyOrder(AiSpotSetting.VALLEY, AiSpotSetting.MOUNTAIN);
        assertThat(result.conditions().hasWater()).isTrue();
        assertThat(result.conditions().hasToilet()).isTrue();
        assertThat(result.conditions().excludeWarning()).isTrue();
        assertThat(result.conditions().hideClosed()).isTrue();
        assertThat(result.conditions().unparsed()).containsExactly("주말", "조용한");
        assertThat(result.usage()).isEqualTo(new TokenUsage(321, 45));
    }

    @Test
    @DisplayName("[F-27] 반경이 50km를 넘으면 50km로 줄인다")
    void clampsRadius() {
        // given
        Map<String, Object> content = content();
        content.put("radiusKm", 80);
        stubOk(content, 10, 5);

        // when
        SpotQueryInterpretation result = interpreter.interpret(SENTENCE);

        // then
        assertThat(result.conditions().radiusKm()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("[F-27] 지역이 공백이고 배열이 비어 있으면 지역 없음, 빈 집합으로 읽는다")
    void readsBlankRegionAndEmptyArrays() {
        // given
        Map<String, Object> content = content();
        content.put("region", "   ");
        content.put("unparsed", List.of("  ", "조용한", "조용한"));
        stubOk(content, 10, 5);

        // when
        SpotQueryInterpretation result = interpreter.interpret(SENTENCE);

        // then
        assertThat(result.conditions().region()).isNull();
        assertThat(result.conditions().radiusKm()).isNull();
        assertThat(result.conditions().types()).isEmpty();
        assertThat(result.conditions().settings()).isEmpty();
        assertThat(result.conditions().unparsed()).containsExactly("조용한");
    }

    @Test
    @DisplayName("[F-27] 요청은 모델, 저장 안 함, 엄격한 JSON 스키마를 담고 문장은 user 메시지에만 넣는다")
    void sendsExpectedRequest() {
        // given
        stubOk(content(), 10, 5);

        // when
        interpreter.interpret(SENTENCE);

        // then
        LoggedRequest request = onlyRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-openai-key");
        JsonNode body = jsonMapper.readTree(request.getBodyAsString());
        assertThat(body.get("model").asString()).isEqualTo("gpt-4.1-mini");
        assertThat(body.get("store").asBoolean()).isFalse();
        assertThat(body.get("temperature").asInt()).isZero();
        assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(300);
        JsonNode format = body.get("response_format");
        assertThat(format.get("type").asString()).isEqualTo("json_schema");
        assertThat(format.get("json_schema").get("strict").asBoolean()).isTrue();
        JsonNode schema = format.get("json_schema").get("schema");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.get("required").size()).isEqualTo(9);
        JsonNode messages = body.get("messages");
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).get("role").asString()).isEqualTo("system");
        assertThat(messages.get(0).get("content").asString()).doesNotContain(SENTENCE);
        assertThat(messages.get(1).get("role").asString()).isEqualTo("user");
        assertThat(messages.get(1).get("content").asString()).isEqualTo(SENTENCE);
    }

    @Test
    @DisplayName("[F-27] 응답이 읽기 시간 안에 오지 않으면 실패하고 다시 보내지 않는다")
    void failsOnReadTimeoutWithoutRetry() {
        // given
        OpenAiProperties shortTimeout = new OpenAiProperties(
                wireMock.baseUrl() + "/openai/v1",
                "test-openai-key",
                "gpt-4.1-mini",
                Duration.ofSeconds(1),
                Duration.ofMillis(300),
                300);
        OpenAiSpotQueryInterpreter slow = new OpenAiSpotQueryInterpreter(
                shortTimeout, new ExternalApiProperties(10, Duration.ZERO), jsonMapper, metrics);
        stub(okResponse(content(), 10, 5).withFixedDelay(1500));

        // when & then
        SpotQueryInterpretationException e =
                catchThrowableOfType(SpotQueryInterpretationException.class, () -> slow.interpret(SENTENCE));
        assertThat(e.getMessage()).doesNotContain(SENTENCE);
        assertThat(e.usage()).isEmpty();
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] HTTP 401과 500이면 상태를 메시지에 담아 실패하고 다시 보내지 않는다")
    void failsOnHttpErrors() {
        // given
        stub(aResponse().withStatus(401).withBody("{\"error\":\"" + SENTENCE + "\"}"));

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE))
                .isInstanceOf(SpotQueryInterpretationException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining(SENTENCE);
        assertThat(requestCount()).isEqualTo(1);

        wireMock.resetAll();
        stub(aResponse().withStatus(500).withBody("oops"));
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE))
                .isInstanceOf(SpotQueryInterpretationException.class)
                .hasMessageContaining("500");
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] 응답 본문이 JSON이 아니면 실패한다")
    void failsWhenBodyIsNotJson() {
        // given
        stub(aResponse().withStatus(200).withBody("<html>" + SENTENCE + "</html>"));

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE))
                .isInstanceOf(SpotQueryInterpretationException.class)
                .hasMessageNotContaining(SENTENCE);
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] 토큰 사용량이 없는 응답은 실패한다")
    void failsWhenUsageMissing() {
        // given
        Map<String, Object> body = completion("stop", jsonMapper.writeValueAsString(content()), null);
        stub(aResponse().withStatus(200).withBody(jsonMapper.writeValueAsString(body)));

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);
    }

    @Test
    @DisplayName("[F-27] 답변 내용이 JSON이 아니면 실패하고 토큰 수를 예외에 싣는다")
    void failsWhenContentIsNotJson() {
        // given
        stubRawContent("그냥 문장입니다", "stop", 100, 7);

        // when
        SpotQueryInterpretationException e =
                catchThrowableOfType(SpotQueryInterpretationException.class, () -> interpreter.interpret(SENTENCE));

        // then
        assertThat(e.usage()).contains(new TokenUsage(100, 7));
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] 모르는 enum 값이 있으면 실패하고 토큰 수를 예외에 싣는다")
    void failsOnUnknownEnumValue() {
        // given
        Map<String, Object> content = content();
        content.put("types", List.of("HOTEL"));
        stubOk(content, 200, 20);

        // when
        SpotQueryInterpretationException e =
                catchThrowableOfType(SpotQueryInterpretationException.class, () -> interpreter.interpret(SENTENCE));

        // then
        assertThat(e.getMessage()).contains("types").doesNotContain("HOTEL");
        assertThat(e.usage()).contains(new TokenUsage(200, 20));
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] 모르는 키가 있으면 실패한다")
    void failsOnUnknownKey() {
        // given
        Map<String, Object> content = content();
        content.put("recommendation", "여기가 좋아요");
        stubOk(content, 10, 5);

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] 빠진 키가 있으면 실패한다")
    void failsOnMissingKey() {
        // given
        Map<String, Object> content = content();
        content.remove("hideClosed");
        stubOk(content, 10, 5);

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);
    }

    @Test
    @DisplayName("[F-27] 반경이 0 이하이거나 값 유형이 틀리면 실패한다")
    void failsOnWrongValueTypes() {
        // given
        Map<String, Object> zeroRadius = content();
        zeroRadius.put("radiusKm", 0);
        stubOk(zeroRadius, 10, 5);

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);

        wireMock.resetAll();
        Map<String, Object> stringBoolean = content();
        stringBoolean.put("hasWater", "true");
        stubOk(stringBoolean, 10, 5);
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);
    }

    @Test
    @DisplayName("[F-27] unparsed가 10개를 넘거나 항목이 50자를 넘으면 실패한다")
    void failsWhenUnparsedTooLarge() {
        // given
        Map<String, Object> tooMany = content();
        tooMany.put("unparsed", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"));
        stubOk(tooMany, 10, 5);

        // when & then
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);

        wireMock.resetAll();
        Map<String, Object> tooLong = content();
        tooLong.put("unparsed", List.of("가".repeat(51)));
        stubOk(tooLong, 10, 5);
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);
    }

    @Test
    @DisplayName("[F-27] 모델이 답변을 거절하면 실패한다")
    void failsOnRefusal() {
        // given
        Map<String, Object> body = completion("stop", null, new TokenUsage(30, 3));
        @SuppressWarnings("unchecked")
        Map<String, Object> message =
                (Map<String, Object>) ((Map<String, Object>) ((List<?>) body.get("choices")).getFirst()).get("message");
        message.put("refusal", "답할 수 없습니다");
        stub(aResponse().withStatus(200).withBody(jsonMapper.writeValueAsString(body)));

        // when
        SpotQueryInterpretationException e =
                catchThrowableOfType(SpotQueryInterpretationException.class, () -> interpreter.interpret(SENTENCE));

        // then
        assertThat(e.getMessage()).doesNotContain("답할 수 없습니다");
        assertThat(e.usage()).contains(new TokenUsage(30, 3));
    }

    @Test
    @DisplayName("[F-27] 답변이 길이 제한으로 잘리면 실패한다")
    void failsOnTruncatedAnswer() {
        // given
        stubRawContent("{\"region\":\"설", "length", 100, 300);

        // when
        SpotQueryInterpretationException e =
                catchThrowableOfType(SpotQueryInterpretationException.class, () -> interpreter.interpret(SENTENCE));

        // then
        assertThat(e.usage()).contains(new TokenUsage(100, 300));
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-27] 키가 비어 있으면 요청을 보내지 않고 실패한다")
    void failsWithoutSendingWhenKeyBlank() {
        // given
        OpenAiProperties noKey = new OpenAiProperties(
                wireMock.baseUrl() + "/openai/v1",
                null,
                "gpt-4.1-mini",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                300);
        OpenAiSpotQueryInterpreter keyless = new OpenAiSpotQueryInterpreter(
                noKey, new ExternalApiProperties(10, Duration.ZERO), jsonMapper, metrics);

        // when & then
        assertThatThrownBy(() -> keyless.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);
        assertThat(requestCount()).isZero();
    }

    @Test
    @DisplayName("[F-27] 성공과 실패를 외부 API 호출 지표에 나눠 기록한다")
    void recordsMetrics() {
        // given
        long successBefore = timerCount("success");
        long failureBefore = timerCount("failure");
        stubOk(content(), 10, 5);
        interpreter.interpret(SENTENCE);
        wireMock.resetAll();
        stub(aResponse().withStatus(500));

        // when
        assertThatThrownBy(() -> interpreter.interpret(SENTENCE)).isInstanceOf(SpotQueryInterpretationException.class);

        // then
        assertThat(timerCount("success") - successBefore).isEqualTo(1);
        assertThat(timerCount("failure") - failureBefore).isEqualTo(1);
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

    private Map<String, Object> completion(String finishReason, String content, TokenUsage usage) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("refusal", null);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finishReason);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("choices", List.of(choice));
        if (usage != null) {
            body.put(
                    "usage",
                    Map.of(
                            "prompt_tokens", usage.inputTokens(),
                            "completion_tokens", usage.outputTokens()));
        }
        return body;
    }

    private ResponseDefinitionBuilder okResponse(Map<String, Object> content, int input, int output) {
        Map<String, Object> body =
                completion("stop", jsonMapper.writeValueAsString(content), new TokenUsage(input, output));
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonMapper.writeValueAsString(body));
    }

    private void stubOk(Map<String, Object> content, int input, int output) {
        stub(okResponse(content, input, output));
    }

    private void stubRawContent(String rawContent, String finishReason, int input, int output) {
        Map<String, Object> body = completion(finishReason, rawContent, new TokenUsage(input, output));
        stub(aResponse().withStatus(200).withBody(jsonMapper.writeValueAsString(body)));
    }

    private void stub(ResponseDefinitionBuilder response) {
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(response));
    }

    private int requestCount() {
        return wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH))).size();
    }

    private LoggedRequest onlyRequest() {
        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlPathEqualTo(PATH)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private long timerCount(String outcome) {
        Timer timer = meterRegistry
                .find(REQUEST_TIMER)
                .tags("api", "openai", "operation", "chatCompletions", "outcome", outcome)
                .timer();
        return timer == null ? 0 : timer.count();
    }
}
