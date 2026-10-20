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

/**
 * 단기예보 발표가 바뀐 직후 같은 격자에 요청이 몰려도 서버가 기상청을 한 번만 부르는지 확인한다.
 *
 * <p>캐시 키에 발표 시각이 들어 있어서, 서버는 새 발표를 쓰기 시작하는 순간 모든 격자의 값을 새로 받아야 한다. 이때 같은 격자로 동시에 들어온
 * 요청은 첫 요청의 기상청 호출을 함께 기다린다. 그래서 기상청 동시 호출 한도(10)를 넘는 요청이 몰려도 날씨 없이 끝나는 요청이 없어야 한다.
 * 호출 수와 예보를 받은 요청 수는 수정 전후를 비교하려고 로그로도 남긴다.
 */
@Slf4j
@IntegrationTest
class WeatherShortTermBurstIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String REQUEST_TIMER = "moewadeul.external.api.requests";
    private static final int THREADS = 100;
    // 테스트 프로필의 응답 대기 시간은 1초다. 느린 CI 러너에서는 지연이 길면 동시 요청 일부가 대기 시간을 넘겨 예보 없이 끝나므로, 짧게 둔다.
    private static final int SHORT_TERM_DELAY_MILLIS = 300;
    // 서울시청, 기상청 격자 60·127
    private static final int NX = 60;
    private static final int NY = 127;
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;
    // 한국 시각 13:58에는 11시 발표를, 14:10부터는 14시 발표를 쓴다. 중기예보는 둘 다 06시 발표라서 캐시에 남는다.
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
        // given: 직전 발표의 단기예보와 중기예보를 캐시에 채운다.
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
        // given: 직전 발표를 캐시에 채운 뒤, 새 발표의 단기예보는 늦게 실패하게 둔다.
        fillPreviousPublish();
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(serverError().withFixedDelay(SHORT_TERM_DELAY_MILLIS)));
        long callsBefore = shortTermCallCount();

        // when
        List<Optional<WeatherForecast>> results = forecastConcurrently();

        // then: 서버는 첫 호출 한 번(실패한 응답의 재시도 1회 포함)만 보내고, 기다리던 요청도 모두 날씨 없이 끝난다.
        assertThat(results).allMatch(Optional::isEmpty);
        assertThat(shortTermCallCount() - callsBefore).isEqualTo(2);

        // when: 기상청이 회복한 뒤 같은 격자를 다시 읽는다.
        wireMock.resetAll();
        stubAll(0);
        Optional<WeatherForecast> retried = service.forecast(NX, NY, SEOUL_LAT, SEOUL_LNG);

        // then: 실패한 결과는 캐시에 남지 않아서, 서버가 기상청을 다시 부르고 예보를 돌려준다.
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

    // 서버는 재시도를 포함해 기상청에 보낸 요청마다 시간을 잰다. WireMock은 지연 응답을 다 보낸 뒤에야 요청을 기록해서, 클라이언트가
    // 기다리다 끊은 요청은 기록에 늦게 남거나 빠진다. 그래서 호출 수는 서버의 지표로 센다.
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
