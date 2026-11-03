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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(
        properties = {
            "moewadeul.weather.max-in-flight=1",
            "moewadeul.weather.response-deadline=500ms",
            "moewadeul.weather.kma.read-timeout=3s"
        })
class SpotWeatherAdmissionIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
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
    @DisplayName("[F-09][NFR-05] 동시에 처리 중인 날씨 요청이 한도에 닿으면 기다리지 않고 바로 빈 값을 돌려주고 거부 지표를 올린다")
    void rejectsImmediatelyWhenAdmissionIsFull() {
        // given
        long spotId = insertSeoulCampsite();
        stubKma(SLOW_MILLIS);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        double rejectedBefore = rejectedCount();
        CompletableFuture<Optional<SpotWeather>> admitted = spotWeatherQueryService.findWeather(spotId);

        // when
        CompletableFuture<Optional<SpotWeather>> rejected = spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(rejected).isCompletedWithValue(Optional.empty());
        assertThat(rejectedCount() - rejectedBefore).isEqualTo(1);
        assertThat(admitted.join()).isEmpty();
        awaitPermitReturned(spotId);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답을 포기한 뒤에도 외부 호출이 끝날 때까지 입장 자리를 쥐고 있다가, 끝나면 돌려준다")
    void holdsPermitUntilAbandonedWorkEnds() {
        // given
        long spotId = insertSeoulCampsite();
        stubKma(SLOW_MILLIS);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        assertThat(spotWeatherQueryService.findWeather(spotId).join()).isEmpty();
        double rejectedBefore = rejectedCount();

        // when
        CompletableFuture<Optional<SpotWeather>> whileAbandonedWorkRuns = spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(whileAbandonedWorkRuns).isCompletedWithValue(Optional.empty());
        assertThat(rejectedCount() - rejectedBefore).isEqualTo(1);
        awaitPermitReturned(spotId);
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
    }

    private void awaitPermitReturned(long spotId) {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> spotWeatherQueryService.findWeather(spotId).join().isPresent());
    }

    private double rejectedCount() {
        Counter counter =
                meterRegistry.find("moewadeul.weather.admission.rejected").counter();
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

    private void stubKma(int shortTermDelayMillis) {
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(okJson(readFixture("kma/vilage-fcst.json")).withFixedDelay(shortTermDelayMillis)));
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
