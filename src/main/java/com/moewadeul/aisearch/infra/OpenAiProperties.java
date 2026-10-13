package com.moewadeul.aisearch.infra;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenAI Chat Completions 호출 설정.
 *
 * @param baseUrl OpenAI API의 기본 주소
 * @param apiKey OpenAI 키. 비밀값이라 환경 변수로만 받는다. 값이 없으면 앱은 뜨지만 AI 검색 호출이 요청을 보내기 전에 실패한다.
 * @param model 문장을 해석할 모델
 * @param connectTimeout 서버와 연결을 맺을 때까지 기다리는 시간
 * @param readTimeout 요청을 보낸 뒤 응답을 받을 때까지 기다리는 시간. 사용자가 화면에서 기다리므로 짧게 둔다.
 * @param maxOutputTokens 답변 길이 상한. 조건 JSON은 짧아서 작게 둔다.
 */
@ConfigurationProperties("moewadeul.aisearch.openai")
public record OpenAiProperties(
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        String apiKey,
        @DefaultValue("gpt-4.1-mini") String model,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("300") int maxOutputTokens) {

    private static final String PREFIX = "moewadeul.aisearch.openai.";

    public OpenAiProperties {
        requireNotBlank(baseUrl, "baseUrl");
        requireNotBlank(model, "model");
        apiKey = Objects.requireNonNullElse(apiKey, "");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(PREFIX + "maxOutputTokens must be positive");
        }
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(PREFIX + name + " must not be blank");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    /** record가 만드는 기본 toString은 키를 그대로 출력한다. 누군가 설정을 로그에 찍어도 키가 남지 않게 가린다. */
    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + baseUrl + ", apiKey=****, model=" + model + ", connectTimeout="
                + connectTimeout + ", readTimeout=" + readTimeout + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
