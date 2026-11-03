package com.moewadeul.spot.api.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"moewadeul.weather.response-deadline=500ms", "moewadeul.weather.kma.read-timeout=3s"})
@Import(SpotWeatherHttpIntegrationTest.DispatchLogConfig.class)
class SpotWeatherHttpDeadlineIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final Duration DEADLINE = Duration.ofMillis(500);
    private static final int SLOW_MILLIS = 2000;

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void captureLogs() {
        logs.start();
        rootLogger().addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger().detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 응답 제한 시간을 넘기면 비동기 재디스패치를 거쳐 200과 weather null로 제한 시간 근처에 응답한다")
    void answersNullWeatherThroughAsyncDispatchWhenKmaOutlastsDeadline() throws Exception {
        // given
        long spotId = insertCampsite();
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(okJson(readFixture("kma/vilage-fcst.json")).withFixedDelay(SLOW_MILLIS)));

        // when
        long startedAt = System.nanoTime();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/spots/" + spotId + "/weather"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(response.body()).has("weather")).isTrue();
        assertThat(jsonMapper.readTree(response.body()).get("weather").isNull()).isTrue();
        assertThat(elapsed).isBetween(DEADLINE.minusMillis(50), Duration.ofMillis(SLOW_MILLIS));
        String traceId = response.headers().firstValue("X-Trace-Id").orElseThrow();
        assertThat(dispatchesOf(traceId)).containsExactly("dispatch REQUEST", "dispatch ASYNC");
    }

    private List<String> dispatchesOf(String traceId) {
        return List.copyOf(logs.list).stream()
                .filter(event -> traceId.equals(event.getMDCPropertyMap().get("traceId")))
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("dispatch "))
                .toList();
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private long insertCampsite() {
        clock.setInstant(MutableClock.DEFAULT_INSTANT);
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText('POINT(37.5665 126.978)', 4326), 60, 127, 'ACTIVE',"
                        + " NOW(6), NOW(6))",
                name);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private static String readFixture(String path) {
        try {
            return new ClassPathResource("fixtures/weather/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
