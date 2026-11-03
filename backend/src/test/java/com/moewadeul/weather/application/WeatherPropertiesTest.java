package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.weather.infra.WeatherCallProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class WeatherPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("[F-09][NFR-05] 설정이 없으면 응답 제한 3초, 동시 처리 500건, 기상청·천문연 동시 호출 25건으로 쓴다")
    void usesDefaultsWhenNothingConfigured() {
        runner.run(context -> {
            WeatherProperties properties = context.getBean(WeatherProperties.class);

            assertThat(properties.responseDeadline()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.maxInFlight()).isEqualTo(500);
            assertThat(context.getBean(WeatherCallProperties.class).maxConcurrentCalls())
                    .isEqualTo(25);
        });
    }

    @Test
    @DisplayName("[F-09][NFR-05] 환경변수 MOEWADEUL_WEATHER_* 로 세 값을 바꿀 수 있고, 기상청 설정과 같은 접두어를 써도 서로 섞이지 않는다")
    void bindsEnvironmentVariables() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                "env",
                                Map.of(
                                        "MOEWADEUL_WEATHER_RESPONSE_DEADLINE", "5s",
                                        "MOEWADEUL_WEATHER_MAX_IN_FLIGHT", "40",
                                        "MOEWADEUL_WEATHER_MAX_CONCURRENT_CALLS", "12",
                                        "MOEWADEUL_WEATHER_KMA_READ_TIMEOUT", "7s"))))
                .run(context -> {
                    WeatherProperties properties = context.getBean(WeatherProperties.class);

                    assertThat(properties.responseDeadline()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.maxInFlight()).isEqualTo(40);
                    assertThat(context.getBean(WeatherCallProperties.class).maxConcurrentCalls())
                            .isEqualTo(12);
                });
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답 제한이 0이거나 한도가 1 미만이면 기동 때 거부한다")
    void rejectsNonPositiveValues() {
        assertThatThrownBy(() -> new WeatherProperties(Duration.ZERO, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responseDeadline");
        assertThatThrownBy(() -> new WeatherProperties(Duration.ofSeconds(3), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxInFlight");
        assertThatThrownBy(() -> new WeatherCallProperties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentCalls");
    }

    @EnableConfigurationProperties({WeatherProperties.class, WeatherCallProperties.class})
    static class PropertiesConfig {}
}
