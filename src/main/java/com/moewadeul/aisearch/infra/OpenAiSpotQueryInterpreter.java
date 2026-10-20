package com.moewadeul.aisearch.infra;

import com.moewadeul.aisearch.application.AiSpotSetting;
import com.moewadeul.aisearch.application.AiSpotType;
import com.moewadeul.aisearch.application.SpotQueryConditions;
import com.moewadeul.aisearch.application.SpotQueryInterpretation;
import com.moewadeul.aisearch.application.SpotQueryInterpretationException;
import com.moewadeul.aisearch.application.SpotQueryInterpreter;
import com.moewadeul.aisearch.application.TokenUsage;
import com.moewadeul.common.external.ExternalApiMetrics;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Chat Completions로 문장을 장소 검색 조건으로 바꾼다.
 *
 * <p>호출이 돈과 시간을 쓰고 사용자가 화면에서 기다리므로 재시도하지 않는다. 실패하면 호출한 쪽이 바로 알고 안내한다.
 *
 * <p>사용자 문장은 개인정보일 수 있고 요청 헤더에는 키가 들어 있다. 그래서 문장, 응답 본문, 헤더는 예외 메시지와 로그에 남기지 않고,
 * 실패 종류와 HTTP 상태만 남긴다.
 */
@Slf4j
@Component
public class OpenAiSpotQueryInterpreter implements SpotQueryInterpreter {

    private static final String API = "openai";
    private static final String OPERATION = "chatCompletions";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String STOP = "stop";
    private static final Set<String> RESULT_KEYS = Set.of(
            "region",
            "radiusKm",
            "types",
            "hasWater",
            "hasToilet",
            "excludeWarning",
            "hideClosed",
            "settings",
            "unparsed");
    private static final int MAX_TEXT_LENGTH = 50;
    private static final int MAX_UNPARSED = 10;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper;
    private final ExternalApiMetrics metrics;

    public OpenAiSpotQueryInterpreter(OpenAiProperties properties, JsonMapper jsonMapper, ExternalApiMetrics metrics) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.metrics = metrics;
        this.restClient = createRestClient(properties);
    }

    @Override
    public SpotQueryInterpretation interpret(String query) {
        if (properties.apiKey().isBlank()) {
            throw fail("OpenAI 키가 설정되지 않았다");
        }
        try {
            return metrics.record(API, OPERATION, () -> interpretOnce(query));
        } catch (SpotQueryInterpretationException e) {
            log.warn("AI 검색 문장 해석이 실패했다. reason={}", e.getMessage());
            throw e;
        }
    }

    private SpotQueryInterpretation interpretOnce(String query) {
        RawResponse response = send(query);
        if (!response.status().is2xxSuccessful()) {
            throw fail("OpenAI가 HTTP " + response.status().value() + "를 돌려줬다");
        }
        JsonNode root = readTree(response.body(), "응답이 JSON이 아니다");
        TokenUsage usage = readUsage(root);
        try {
            return new SpotQueryInterpretation(readConditions(root), usage);
        } catch (SpotQueryInterpretationException e) {
            // 응답은 받았으므로 토큰 비용은 이미 들었다. 호출한 쪽이 하루 한도에 더할 수 있게 실어 보낸다.
            throw new SpotQueryInterpretationException(e.getMessage(), e.getCause(), usage);
        }
    }

    private RawResponse send(String query) {
        try {
            return restClient
                    .post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(query))
                    .exchange((request, response) -> read(response));
        } catch (ResourceAccessException e) {
            throw fail("시간 초과 또는 I/O 오류 " + e.getMostSpecificCause().getClass().getSimpleName(), e);
        }
    }

    private static RawResponse read(ClientHttpResponse response) throws IOException {
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        return new RawResponse(response.getStatusCode(), body);
    }

    // 사용자 문장은 user 메시지에만 넣는다. 날짜, 회원 ID, 이메일, 좌표 같은 다른 값은 보내지 않는다.
    private Map<String, Object> requestBody(String query) {
        return Map.of(
                "model", properties.model(),
                "temperature", 0,
                "max_completion_tokens", properties.maxOutputTokens(),
                // 요청과 응답을 OpenAI 쪽에 저장하지 않게 한다.
                "store", false,
                "messages",
                        List.of(
                                Map.of("role", "system", "content", OpenAiPrompt.SYSTEM),
                                Map.of("role", "user", "content", query)),
                "response_format",
                        Map.of(
                                "type",
                                "json_schema",
                                "json_schema",
                                Map.of(
                                        "name", OpenAiPrompt.SCHEMA_NAME,
                                        "strict", true,
                                        "schema", OpenAiPrompt.SCHEMA)));
    }

    private TokenUsage readUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        JsonNode input = usage.path("prompt_tokens");
        JsonNode output = usage.path("completion_tokens");
        if (!input.isIntegralNumber() || !output.isIntegralNumber() || input.asInt() < 0 || output.asInt() < 0) {
            throw fail("응답에 토큰 사용량이 없다");
        }
        return new TokenUsage(input.asInt(), output.asInt());
    }

    private SpotQueryConditions readConditions(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        if (!STOP.equals(choice.path("finish_reason").asString(null))) {
            throw fail("답변이 끝까지 만들어지지 않았다 (잘림 등)");
        }
        JsonNode message = choice.path("message");
        if (!message.path("refusal").isMissingNode() && !message.path("refusal").isNull()) {
            throw fail("OpenAI가 답변을 거절했다");
        }
        JsonNode content = message.path("content");
        if (!content.isString()) {
            throw fail("답변 내용이 없다");
        }
        JsonNode result = readTree(content.asString(), "답변 내용이 JSON이 아니다");
        return toConditions(result);
    }

    private SpotQueryConditions toConditions(JsonNode result) {
        if (!result.isObject() || result.size() != RESULT_KEYS.size()) {
            throw schemaMismatch("키 구성");
        }
        for (String key : RESULT_KEYS) {
            if (!result.has(key)) {
                throw schemaMismatch("키 구성");
            }
        }
        return new SpotQueryConditions(
                readRegion(result.get("region")),
                readRadius(result.get("radiusKm")),
                readEnums(result.get("types"), AiSpotType::valueOf, "types"),
                readBoolean(result.get("hasWater"), "hasWater"),
                readBoolean(result.get("hasToilet"), "hasToilet"),
                readBoolean(result.get("excludeWarning"), "excludeWarning"),
                readBoolean(result.get("hideClosed"), "hideClosed"),
                readEnums(result.get("settings"), AiSpotSetting::valueOf, "settings"),
                readUnparsed(result.get("unparsed")));
    }

    private String readRegion(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isString() || node.asString().length() > MAX_TEXT_LENGTH) {
            throw schemaMismatch("region");
        }
        return node.asString().strip();
    }

    private Double readRadius(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isNumber() || node.asDouble() <= 0) {
            throw schemaMismatch("radiusKm");
        }
        return node.asDouble();
    }

    private boolean readBoolean(JsonNode node, String field) {
        if (!node.isBoolean()) {
            throw schemaMismatch(field);
        }
        return node.asBoolean();
    }

    private <E> Set<E> readEnums(JsonNode node, Function<String, E> parser, String field) {
        if (!node.isArray()) {
            throw schemaMismatch(field);
        }
        Set<E> values = new LinkedHashSet<>();
        for (JsonNode element : node.values()) {
            if (!element.isString()) {
                throw schemaMismatch(field);
            }
            try {
                values.add(parser.apply(element.asString()));
            } catch (IllegalArgumentException e) {
                throw schemaMismatch(field);
            }
        }
        return values;
    }

    private List<String> readUnparsed(JsonNode node) {
        if (!node.isArray() || node.size() > MAX_UNPARSED) {
            throw schemaMismatch("unparsed");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode element : node.values()) {
            if (!element.isString() || element.asString().length() > MAX_TEXT_LENGTH) {
                throw schemaMismatch("unparsed");
            }
            String text = element.asString().strip();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private JsonNode readTree(String text, String failure) {
        try {
            return jsonMapper.readTree(text);
        } catch (JacksonException e) {
            // 예외 원인에는 응답 일부가 들어갈 수 있어서 원인으로 잇지 않는다.
            throw fail(failure);
        }
    }

    // 어느 필드가 틀렸는지 알아야 원인을 좁힐 수 있다. 값은 응답 내용이라 메시지에 넣지 않고 필드 이름만 넣는다.
    private static SpotQueryInterpretationException schemaMismatch(String field) {
        return fail("답변이 스키마와 다르다 field=" + field);
    }

    private static SpotQueryInterpretationException fail(String reason) {
        return new SpotQueryInterpretationException("AI 검색 해석 실패: " + reason);
    }

    private static SpotQueryInterpretationException fail(String reason, Throwable cause) {
        return new SpotQueryInterpretationException("AI 검색 해석 실패: " + reason, cause);
    }

    private static RestClient createRestClient(OpenAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        // 압축 응답을 받으면, 응답 대기 시간이 지나 다른 스레드가 본문을 닫는 순간 압축 해제기가 IOException이 아닌 IllegalStateException을
        // 던질 수 있다. 그 예외는 RestClient가 I/O 오류로 감싸지 않아서 이 클라이언트의 실패 처리를 건너뛴다. 그래서 압축을 받지 않는다.
        requestFactory.enableCompression(false);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private record RawResponse(HttpStatusCode status, String body) {}
}
