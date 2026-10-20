package com.moewadeul.weather.application;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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
 * 단기예보 발표가 바뀐 직후 같은 격자에 요청이 몰리면 기상청을 몇 번 부르는지 센다.
 *
 * <p>캐시 키에 발표 시각이 들어 있어서, 서버는 새 발표를 쓰기 시작하는 순간 모든 격자의 값을 새로 받아야 한다. 값이 없을 때 서버는 잠금 없이
 * 기상청을 부르므로, 첫 응답이 캐시에 들어가기 전에 들어온 요청은 각자 기상청을 부를 수 있다. 다만 기상청에 동시에 보내는 호출은 한도(10)까지만
 * 나가고, 넘는 요청은 기다리지 않고 날씨 없이 끝난다. 이 테스트는 그 호출 수를 로그로 남기고, 호출 수가 한도를 넘지 않는지 확인한다.
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
    // 외부 API 하나에 동시에 보내는 호출 수의 기본 한도다.
    private static final int CONCURRENT_CALL_LIMIT = 10;
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
    @DisplayName("[F-09][NFR-06] 단기예보 발표가 바뀐 직후 같은 격자를 100건 동시에 읽으면, 기상청 호출은 동시 호출 수 한도까지만 나가고 그 호출만 예보를 받는다")
    void countsShortTermCallsWhenPublishTimeChanges() throws Exception {
        // given: 직전 발표의 단기예보와 중기예보를 캐시에 채운다.
        stubAll(0);
        clock.setInstant(BEFORE_PUBLISH);
        assertThat(service.forecast(NX, NY, SEOUL_LAT, SEOUL_LNG)).isPresent();
        clock.setInstant(AFTER_PUBLISH);
        wireMock.resetAll();
        stubAll(SHORT_TERM_DELAY_MILLIS);
        long callsBefore = shortTermCallCount();
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

        // when
        ready.await();
        start.countDown();
        List<Optional<WeatherForecast>> results = new ArrayList<>();
        for (Future<Optional<WeatherForecast>> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // then
        long shortTermCalls = shortTermCallCount() - callsBefore;
        log.info(
                "단기예보 동시 요청 {}건, 기상청 지연 {}ms: 단기예보 호출 {}회(WireMock 기록 {}회), 예보 받은 요청 {}건",
                THREADS,
                SHORT_TERM_DELAY_MILLIS,
                shortTermCalls,
                requestCount(VILAGE_FCST_PATH),
                results.stream().filter(Optional::isPresent).count());
        // 동시 호출 수 한도(10)를 넘은 요청은 기상청을 부르지 않고 날씨 없이 끝난다. 한도 안에 든 요청만 예보를 받는다.
        assertThat(results).anyMatch(Optional::isPresent);
        assertThat(shortTermCalls).isBetween(1L, (long) CONCURRENT_CALL_LIMIT);
        assertThat(requestCount(MID_LAND_FCST_PATH)).isZero();
        assertThat(requestCount(MID_TA_PATH)).isZero();
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
