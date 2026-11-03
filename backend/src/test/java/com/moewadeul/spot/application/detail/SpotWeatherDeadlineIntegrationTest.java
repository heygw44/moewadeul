package com.moewadeul.spot.application.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.weather.application.LocationWeather;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(
        properties = {
            "moewadeul.weather.response-deadline=500ms",
            "moewadeul.weather.kma.read-timeout=3s",
            "moewadeul.weather.kasi.read-timeout=3s"
        })
class SpotWeatherDeadlineIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final Duration DEADLINE = Duration.ofMillis(500);
    private static final int SLOW_MILLIS = 2000;

    @Autowired
    private SpotWeatherQueryService spotWeatherQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 응답 제한 시간을 넘기면 제한 시간 근처에 날씨 없이 응답하고 데드라인 지표를 올린다")
    void answersWithoutWeatherNearDeadlineWhenKmaIsSlow() {
        // given
        long spotId = insertSeoulCampsite();
        stubKma(readFixture("kma/vilage-fcst.json"), SLOW_MILLIS);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        double deadlinesBefore = deadlineCount("kma");

        // when
        long startedAt = System.nanoTime();
        Optional<LocationWeather> weather =
                spotWeatherQueryService.findWeather(spotId).join();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(weather).isEmpty();
        assertThat(elapsed).isBetween(DEADLINE.minusMillis(50), DEADLINE.plusMillis(500));
        assertThat(deadlineCount("kma") - deadlinesBefore).isEqualTo(1);
        awaitCached(spotId);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 제한 시간을 넘긴 기상청 호출은 끝까지 이어져 캐시에 남고, 다음 요청은 기상청을 다시 부르지 않고 값을 받는다")
    void abandonedKmaCallLandsInCacheForNextRequest() {
        // given
        long spotId = insertSeoulCampsite();
        stubKma(readFixture("kma/vilage-fcst.json"), SLOW_MILLIS);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        assertThat(spotWeatherQueryService.findWeather(spotId).join()).isEmpty();

        // when
        LocationWeather weather = awaitCached(spotId);

        // then
        assertThat(weather.forecast().shortTerm()).isNotEmpty();
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
        assertThat(requestCount(MID_LAND_FCST_PATH)).isEqualTo(1);
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청은 제때 오고 천문연만 늦으면 weather는 채우고 sun만 null이며, 늦은 출몰시각은 다음 요청이 받는다")
    void leavesSunNullWhenOnlyKasiIsSlow() {
        // given
        long spotId = insertSeoulCampsite();
        stubKma(readFixture("kma/vilage-fcst.json"), 0);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")).withFixedDelay(SLOW_MILLIS));
        double kasiDeadlinesBefore = deadlineCount("kasi");
        double kmaDeadlinesBefore = deadlineCount("kma");

        // when
        long startedAt = System.nanoTime();
        Optional<LocationWeather> weather =
                spotWeatherQueryService.findWeather(spotId).join();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(weather).hasValueSatisfying(found -> {
            assertThat(found.forecast().shortTerm()).isNotEmpty();
            assertThat(found.sun()).isNull();
        });
        assertThat(elapsed).isBetween(DEADLINE.minusMillis(50), DEADLINE.plusMillis(500));
        assertThat(deadlineCount("kasi") - kasiDeadlinesBefore).isEqualTo(1);
        assertThat(deadlineCount("kma") - kmaDeadlinesBefore).isZero();
        LocationWeather later = awaitCached(spotId);
        assertThat(later.sun()).isNotNull();
        assertThat(requestCount(RISE_SET_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 제한 시간을 넘긴 뒤에 성공해도 아무도 읽지 않는 출몰시각을 위해 천문연을 부르지 않는다")
    void doesNotCallKasiWhenKmaSucceedsAfterDeadline() {
        // given
        long spotId = insertSeoulCampsite();
        stubKma(readFixture("kma/vilage-fcst.json"), SLOW_MILLIS);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));

        // when
        Optional<LocationWeather> weather =
                spotWeatherQueryService.findWeather(spotId).join();

        // then
        assertThat(weather).isEmpty();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(requestCount(MID_TA_PATH)).isEqualTo(1));
        await().during(Duration.ofMillis(700))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requestCount(RISE_SET_PATH)).isZero());
    }

    private LocationWeather awaitCached(long spotId) {
        return await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(
                        () -> spotWeatherQueryService.findWeather(spotId).join(),
                        found -> found.isPresent() && found.get().sun() != null)
                .orElseThrow();
    }

    private double deadlineCount(String api) {
        Counter counter = meterRegistry
                .find("moewadeul.external.api.deadline")
                .tag("api", api)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private int requestCount(String path) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }

    private long insertSeoulCampsite() {
        clock.setInstant(Instant.parse("2026-10-04T03:58:00Z"));
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText('POINT(37.5665 126.978)', 4326), 60, 127, 'ACTIVE',"
                        + " NOW(6), NOW(6))",
                name);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void stubKma(String shortTermBody, int shortTermDelayMillis) {
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(okJson(shortTermBody).withFixedDelay(shortTermDelayMillis)));
        wireMock.stubFor(
                get(urlPathEqualTo(MID_LAND_FCST_PATH)).willReturn(okJson(readFixture("kma/mid-land-fcst.json"))));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH)).willReturn(okJson(readFixture("kma/mid-ta.json"))));
    }

    private void stubKasi(ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH)).willReturn(response));
    }

    private static ResponseDefinitionBuilder okXml(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml;charset=UTF-8")
                .withBody(body);
    }

    private static String readFixture(String path) {
        try {
            return new ClassPathResource("fixtures/weather/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
