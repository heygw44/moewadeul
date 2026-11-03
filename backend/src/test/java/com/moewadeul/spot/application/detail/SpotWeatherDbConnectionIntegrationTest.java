package com.moewadeul.spot.application.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.weather.application.LocationWeather;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotWeatherDbConnectionIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final int EXTERNAL_DELAY_MILLIS = 300;
    private static final int SAMPLE_INTERVAL_MILLIS = 20;

    @Autowired
    private SpotWeatherQueryService spotWeatherQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-09][NFR-05] 기상청과 천문연 응답을 기다리는 동안에는 DB 커넥션을 하나도 쓰지 않는다")
    void holdsNoDbConnectionWhileWaitingForExternalApis() throws Exception {
        // given
        clock.setInstant(Instant.parse("2026-10-04T03:58:00Z"));
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText('POINT(37.5665 126.978)', 4326), 60, 127, 'ACTIVE',"
                        + " NOW(6), NOW(6))",
                name);
        long spotId = jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        stubSlow(VILAGE_FCST_PATH, okJson(readFixture("kma/vilage-fcst.json")));
        stubSlow(MID_LAND_FCST_PATH, okJson(readFixture("kma/mid-land-fcst.json")));
        stubSlow(MID_TA_PATH, okJson(readFixture("kma/mid-ta.json")));
        stubSlow(
                RISE_SET_PATH,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(readFixture("kasi/rise-set.xml")));
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();

        // when
        CompletableFuture<Optional<LocationWeather>> weather = spotWeatherQueryService.findWeather(spotId);
        List<Integer> activeConnections = new ArrayList<>();
        while (!weather.isDone()) {
            activeConnections.add(pool.getActiveConnections());
            Thread.sleep(SAMPLE_INTERVAL_MILLIS);
        }

        // then
        assertThat(weather.join())
                .hasValueSatisfying(found -> assertThat(found.sun()).isNotNull());
        wireMock.verify(getRequestedFor(urlPathEqualTo(VILAGE_FCST_PATH)));
        wireMock.verify(getRequestedFor(urlPathEqualTo(RISE_SET_PATH)));
        assertThat(activeConnections).hasSizeGreaterThan(20).containsOnly(0);
    }

    private void stubSlow(String path, ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(response.withFixedDelay(EXTERNAL_DELAY_MILLIS)));
    }

    private static String readFixture(String path) {
        try {
            return new ClassPathResource("fixtures/weather/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
