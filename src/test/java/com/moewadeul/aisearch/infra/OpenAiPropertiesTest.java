package com.moewadeul.aisearch.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiPropertiesTest {

    @Test
    @DisplayName("[F-27] 키가 없으면 빈 문자열로 둔다")
    void nullKeyBecomesEmpty() {
        OpenAiProperties properties = new OpenAiProperties(
                "https://example.test/v1", null, "gpt-4.1-mini", Duration.ofSeconds(1), Duration.ofSeconds(5), 300);

        assertThat(properties.apiKey()).isEmpty();
    }

    @Test
    @DisplayName("[F-27] toString에 키가 나오지 않는다")
    void toStringHidesKey() {
        OpenAiProperties properties = new OpenAiProperties(
                "https://example.test/v1",
                "very-secret-value",
                "gpt-4.1-mini",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                300);

        assertThat(properties.toString()).doesNotContain("very-secret-value").contains("gpt-4.1-mini");
    }

    @Test
    @DisplayName("[F-27] 주소가 비었거나 시간·토큰 상한이 0 이하이면 만들 수 없다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new OpenAiProperties(
                        " ", "k", "gpt-4.1-mini", Duration.ofSeconds(1), Duration.ofSeconds(5), 300))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenAiProperties(
                        "https://example.test/v1", "k", "gpt-4.1-mini", Duration.ZERO, Duration.ofSeconds(5), 300))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenAiProperties(
                        "https://example.test/v1",
                        "k",
                        "gpt-4.1-mini",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
