package com.moewadeul.weather.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

// 톰캣 스레드를 얼마나 쥐는지는 MockMvc로 볼 수 없어서 실제 톰캣을 띄우고 HTTP로 요청한다.
@Slf4j
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.tomcat.threads.max=20")
class WeatherTomcatThreadsIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final int WEATHER_REQUESTS = 60;
    private static final int KMA_DELAY_MILLIS = 1_500;
    private static final Duration DB_ONLY_LIMIT = Duration.ofMillis(700);
    private static final Instant NOW = Instant.parse("2026-10-04T05:10:00Z");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-09][NFR-05] 느린 기상청의 차가운 격자 하나로 날씨 요청이 톰캣 스레드 수보다 많이 몰려도, DB만 쓰는 요청은 기다리지 않고 응답한다")
    void dbOnlyRequestStaysResponsiveWhileWeatherRequestsWaitOnOneColdGrid() throws Exception {
        // given
        long spotId = insertSeoulCampsite();
        stubSlowKma();

        // when
        List<CompletableFuture<HttpResponse<String>>> weather = new ArrayList<>();
        for (int i = 0; i < WEATHER_REQUESTS; i++) {
            weather.add(sendAsync("/api/spots/" + spotId + "/weather"));
        }
        await().atMost(Duration.ofSeconds(5))
                .until(() -> wireMock.findAll(getRequestedFor(urlPathEqualTo(VILAGE_FCST_PATH)))
                                .size()
                        == 1);
        Thread.sleep(300);
        long startedAt = System.nanoTime();
        HttpResponse<String> dbOnly = sendAsync("/api/spots/" + spotId).get();
        Duration dbOnlyElapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        log.info("날씨 요청 {}건이 몰린 중 DB만 쓰는 요청의 응답 시간 {}ms", WEATHER_REQUESTS, dbOnlyElapsed.toMillis());
        assertThat(dbOnly.statusCode()).isEqualTo(200);
        assertThat(dbOnlyElapsed).isLessThan(DB_ONLY_LIMIT);
        for (CompletableFuture<HttpResponse<String>> response : weather) {
            assertThat(response.get().statusCode()).isEqualTo(200);
        }
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private long insertSeoulCampsite() {
        clock.setInstant(NOW);
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText('POINT(37.5665 126.978)', 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void stubSlowKma() {
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(okJson(readFixture("kma/vilage-fcst.json")).withFixedDelay(KMA_DELAY_MILLIS)));
        wireMock.stubFor(
                get(urlPathEqualTo(MID_LAND_FCST_PATH)).willReturn(okJson(readFixture("kma/mid-land-fcst.json"))));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH)).willReturn(okJson(readFixture("kma/mid-ta.json"))));
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(readFixture("kasi/rise-set.xml"))));
    }

    private static String readFixture(String path) {
        try {
            return new ClassPathResource("fixtures/weather/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
