package com.moewadeul.weather.application;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

@Slf4j
@IntegrationTest
class WeatherShortTermBurstIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String REQUEST_TIMER = "moewadeul.external.api.requests";
    private static final int THREADS = 100;
    private static final int SHORT_TERM_DELAY_MILLIS = 300;
    private static final int NX = 60;
    private static final int NY = 127;
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;
    private static final Instant BEFORE_PUBLISH = Instant.parse("2026-10-04T04:58:00Z");
    private static final Instant AFTER_PUBLISH = Instant.parse("2026-10-04T05:10:00Z");

    @Autowired
    private WeatherQueryService service;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-09][NFR-06] 단기예보 발표가 바뀐 직후 같은 격자를 100건 동시에 읽어도 서버는 기상청 단기예보를 한 번만 부르고, 100건 모두 예보를 받는다")
    void callsShortTermOnceWhenPublishTimeChanges() throws Exception {
        // given
        fillPreviousPublish();
        stubAll(SHORT_TERM_DELAY_MILLIS);
        long callsBefore = shortTermCallCount();

        // when
        List<Optional<WeatherForecast>> results = forecastConcurrently();

        // then
        long shortTermCalls = shortTermCallCount() - callsBefore;
        long received = results.stream().filter(Optional::isPresent).count();
        log.info(
                "단기예보 동시 요청 {}건, 기상청 지연 {}ms: 단기예보 호출 {}회(WireMock 기록 {}회), 예보 받은 요청 {}건",
                THREADS,
                SHORT_TERM_DELAY_MILLIS,
                shortTermCalls,
                requestCount(VILAGE_FCST_PATH),
                received);
        assertThat(shortTermCalls).isEqualTo(1);
        assertThat(received).isEqualTo(THREADS);
        assertThat(requestCount(MID_LAND_FCST_PATH)).isZero();
        assertThat(requestCount(MID_TA_PATH)).isZero();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 발표가 바뀐 직후 첫 기상청 호출이 실패하면 기다리던 100건 모두 날씨 없이 응답하고, 다음 요청은 기상청을 다시 부른다")
    void allWaitersFallBackWhenFirstCallFailsAndNextRequestRetries() throws Exception {
        // given
        fillPreviousPublish();
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(serverError().withFixedDelay(SHORT_TERM_DELAY_MILLIS)));
        long callsBefore = shortTermCallCount();

        // when
        List<Optional<WeatherForecast>> results = forecastConcurrently();

        // then
        assertThat(results).allMatch(Optional::isEmpty);
        assertThat(shortTermCallCount() - callsBefore).isEqualTo(2);

        // when
        wireMock.resetAll();
        stubAll(0);
        Optional<WeatherForecast> retried = service.forecast(NX, NY, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(retried).isPresent();
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
    }

    private void fillPreviousPublish() {
        stubAll(0);
        clock.setInstant(BEFORE_PUBLISH);
        assertThat(service.forecast(NX, NY, SEOUL_LAT, SEOUL_LNG)).isPresent();
        clock.setInstant(AFTER_PUBLISH);
        wireMock.resetAll();
    }

    private List<Optional<WeatherForecast>> forecastConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<WeatherForecast>>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.forecast(NX, NY, SEOUL_LAT, SEOUL_LNG);
            }));
        }

        ready.await();
        start.countDown();
        List<Optional<WeatherForecast>> results = new ArrayList<>();
        for (Future<Optional<WeatherForecast>> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        return results;
    }

    private void stubAll(int shortTermDelayMillis) {
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(okJson(readFixture("vilage-fcst.json")).withFixedDelay(shortTermDelayMillis)));
        wireMock.stubFor(get(urlPathEqualTo(MID_LAND_FCST_PATH)).willReturn(okJson(readFixture("mid-land-fcst.json"))));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH)).willReturn(okJson(readFixture("mid-ta.json"))));
    }

    private long shortTermCallCount() {
        return meterRegistry.find(REQUEST_TIMER).tag("api", "kma").tag("operation", "getVilageFcst").timers().stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private int requestCount(String path) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }

    private static String readFixture(String fileName) {
        try {
            return new ClassPathResource("fixtures/weather/kma/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
