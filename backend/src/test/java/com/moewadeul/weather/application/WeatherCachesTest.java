package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.moewadeul.weather.application.WeatherForecast.ShortTermForecast;
import com.moewadeul.weather.infra.KmaApiException;
import com.moewadeul.weather.infra.KmaMidTermItem;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherCachesTest {

    private static final int WAITERS = WeatherCaches.MAX_WAITERS - 1;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 4, 14, 0);
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    private static final SunTimes SUN_TIMES =
            new SunTimes(DATE, LocalTime.of(6, 30), LocalTime.of(18, 11), LocalTime.of(18, 38));
    private static final KmaMidTermItem MID_TERM_ITEM = new KmaMidTermItem(Map.of("regId", "11B00000"));

    private final WeatherCaches caches = new WeatherCaches(new SimpleMeterRegistry());
    private final ExecutorService executor = Executors.newFixedThreadPool(WeatherCaches.MAX_WAITERS + 3);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("[F-09][NFR-06] 단기예보 첫 호출이 끝나기 전에 같은 키로 들어온 요청은 기상청을 다시 부르지 않고 첫 호출의 예보를 받는다")
    void sharesShortTermLoadForSameKey() throws Exception {
        // given
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<ShortTermForecast> published = List.of();

        // when
        List<Future<List<ShortTermForecast>>> results = runConcurrently(
                loading,
                release,
                () -> caches.shortTerm(BASE, 60, 127, () -> {
                    calls.incrementAndGet();
                    return awaitThenReturn(loading, release, published);
                }));

        // then
        assertThat(calls).hasValue(1);
        for (Future<List<ShortTermForecast>> result : results) {
            assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(published);
        }
    }

    @Test
    @DisplayName("[F-09][NFR-05] 단기예보 첫 호출이 실패하면 기다리던 요청도 같은 예외를 받고, 다음 요청은 기상청을 다시 부른다")
    void propagatesShortTermFailureAndRetriesNextTime() throws Exception {
        // given
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // when
        List<Future<List<ShortTermForecast>>> results = runConcurrently(
                loading,
                release,
                () -> caches.shortTerm(BASE, 60, 127, () -> {
                    calls.incrementAndGet();
                    awaitThenReturn(loading, release, null);
                    throw new KmaApiException("기상청 응답 실패");
                }));

        // then
        assertThat(calls).hasValue(1);
        for (Future<List<ShortTermForecast>> result : results) {
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(KmaApiException.class);
        }
        List<ShortTermForecast> retried = caches.shortTerm(BASE, 60, 127, () -> {
            calls.incrementAndGet();
            return List.of();
        });
        assertThat(retried).isEmpty();
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("[F-09] 같은 키의 첫 호출이 진행 중이어도 다른 키는 기다리지 않고 바로 부른다")
    void doesNotBlockOtherKeys() throws Exception {
        // given
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<List<ShortTermForecast>> blocked = executor.submit(
                () -> caches.shortTerm(BASE, 60, 127, () -> awaitThenReturn(loading, release, List.of())));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        List<ShortTermForecast> other = caches.shortTerm(BASE, 61, 127, List::of);

        // then
        assertThat(other).isEmpty();
        assertThat(blocked).isNotDone();
        release.countDown();
        assertThat(blocked.get(5, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-06] 중기예보도 같은 키의 첫 호출 결과를 함께 쓰고, 빈 값은 넣지 않아 다음 요청이 다시 부른다")
    void sharesMidTermLoadAndSkipsEmpty() throws Exception {
        // given
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // when
        List<Future<Optional<KmaMidTermItem>>> results = runConcurrently(
                loading,
                release,
                () -> caches.midTerm("getMidTa", "11B10101", BASE, () -> {
                    calls.incrementAndGet();
                    return awaitThenReturn(loading, release, Optional.<KmaMidTermItem>empty());
                }));

        // then
        assertThat(calls).hasValue(1);
        for (Future<Optional<KmaMidTermItem>> result : results) {
            assertThat(result.get(5, TimeUnit.SECONDS)).isEmpty();
        }
        Optional<KmaMidTermItem> retried = caches.midTerm("getMidTa", "11B10101", BASE, () -> {
            calls.incrementAndGet();
            return Optional.of(MID_TERM_ITEM);
        });
        assertThat(retried).contains(MID_TERM_ITEM);
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("[F-09] 출몰시각도 같은 위치·날짜의 첫 호출 결과를 함께 쓰고, 넣은 값은 다시 부르지 않는다")
    void sharesSunTimesLoad() throws Exception {
        // given
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // when
        List<Future<Optional<SunTimes>>> results = runConcurrently(
                loading,
                release,
                () -> caches.sunTimes(37.5665, 126.978, DATE, () -> {
                    calls.incrementAndGet();
                    return awaitThenReturn(loading, release, Optional.of(SUN_TIMES));
                }));

        // then
        for (Future<Optional<SunTimes>> result : results) {
            assertThat(result.get(5, TimeUnit.SECONDS)).contains(SUN_TIMES);
        }
        Optional<SunTimes> cached = caches.sunTimes(37.5665, 126.978, DATE, () -> {
            calls.incrementAndGet();
            return Optional.empty();
        });
        assertThat(cached).contains(SUN_TIMES);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 진행 중인 호출을 기다리는 요청이 10개를 넘으면 넘는 요청은 기다리지 않고 바로 거부하고, 기다리던 요청과 첫 호출은 예보를 받는다")
    void rejectsWaitersBeyondLimitImmediately() throws Exception {
        // given
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<ShortTermForecast> published = List.of();
        Future<List<ShortTermForecast>> owner = executor.submit(
                () -> caches.shortTerm(BASE, 60, 127, () -> awaitThenReturn(loading, release, published)));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        List<Future<List<ShortTermForecast>>> waiting = startWaiters(WeatherCaches.MAX_WAITERS, 60, 127);

        // when & then
        assertThatThrownBy(() -> caches.shortTerm(BASE, 60, 127, List::of))
                .isInstanceOf(WeatherCacheBusyException.class);
        assertThatThrownBy(() -> caches.shortTerm(BASE, 60, 127, List::of))
                .isInstanceOf(WeatherCacheBusyException.class);
        assertThat(owner).isNotDone();
        release.countDown();
        assertThat(owner.get(5, TimeUnit.SECONDS)).isSameAs(published);
        for (Future<List<ShortTermForecast>> result : waiting) {
            assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(published);
        }
    }

    @Test
    @DisplayName("[F-09][NFR-05] 세 캐시가 기다리는 자리를 함께 쓰므로, 단기예보 대기가 가득하면 중기예보와 출몰시각의 대기도 거부한다")
    void sharesWaiterSlotsAcrossCaches() throws Exception {
        // given
        CountDownLatch shortLoading = new CountDownLatch(1);
        CountDownLatch midLoading = new CountDownLatch(1);
        CountDownLatch sunLoading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> caches.shortTerm(BASE, 60, 127, () -> awaitThenReturn(shortLoading, release, List.of())));
        executor.submit(() -> caches.midTerm(
                "getMidTa", "11B10101", BASE, () -> awaitThenReturn(midLoading, release, Optional.empty())));
        executor.submit(() ->
                caches.sunTimes(37.5665, 126.978, DATE, () -> awaitThenReturn(sunLoading, release, Optional.empty())));
        assertThat(shortLoading.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(midLoading.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sunLoading.await(5, TimeUnit.SECONDS)).isTrue();
        startWaiters(WeatherCaches.MAX_WAITERS, 60, 127);

        // when & then
        assertThatThrownBy(() -> caches.midTerm("getMidTa", "11B10101", BASE, Optional::empty))
                .isInstanceOf(WeatherCacheBusyException.class);
        assertThatThrownBy(() -> caches.sunTimes(37.5665, 126.978, DATE, Optional::empty))
                .isInstanceOf(WeatherCacheBusyException.class);
        release.countDown();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기다린 뒤에는 첫 호출이 성공했든 실패했든 자리를 돌려줘서, 다음 요청 10개가 다시 기다릴 수 있다")
    void returnsWaiterSlotsAfterSuccessAndFailure() throws Exception {
        // given
        for (boolean fails : new boolean[] {false, true}) {
            int nx = fails ? 62 : 61;
            CountDownLatch loading = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            List<ShortTermForecast> published = List.of();
            Future<List<ShortTermForecast>> owner = executor.submit(() -> caches.shortTerm(BASE, nx, 127, () -> {
                awaitThenReturn(loading, release, null);
                if (fails) {
                    throw new KmaApiException("기상청 응답 실패");
                }
                return published;
            }));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            List<Future<List<ShortTermForecast>>> waiting = startWaiters(WeatherCaches.MAX_WAITERS, nx, 127);

            // when
            release.countDown();

            // then
            for (Future<List<ShortTermForecast>> result : waiting) {
                if (fails) {
                    assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
                } else {
                    assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(published);
                }
            }
            if (fails) {
                assertThatThrownBy(() -> owner.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            }
        }
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> caches.shortTerm(BASE, 63, 127, () -> awaitThenReturn(loading, release, List.of())));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        List<Future<List<ShortTermForecast>>> waiting = startWaiters(WeatherCaches.MAX_WAITERS, 63, 127);
        release.countDown();
        for (Future<List<ShortTermForecast>> result : waiting) {
            assertThat(result.get(5, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    @DisplayName("[F-09][NFR-06] 이미 끝난 값은 기다리는 자리가 모두 차 있어도 자리를 쓰지 않고 바로 돌려준다")
    void servesCompletedEntriesEvenWhenAllSlotsAreTaken() throws Exception {
        // given
        List<ShortTermForecast> cached = List.of();
        caches.shortTerm(BASE, 61, 127, () -> cached);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> caches.shortTerm(BASE, 60, 127, () -> awaitThenReturn(loading, release, List.of())));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        startWaiters(WeatherCaches.MAX_WAITERS, 60, 127);

        // when
        List<ShortTermForecast> served = caches.shortTerm(BASE, 61, 127, () -> {
            throw new AssertionError("끝난 값은 다시 부르지 않는다");
        });

        // then
        assertThat(served).isSameAs(cached);
        release.countDown();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 자리가 없어 거부된 요청은 기상청 호출을 시작하지 않는다")
    void rejectedWaiterNeverStartsLoad() throws Exception {
        // given
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> caches.shortTerm(BASE, 60, 127, () -> {
            calls.incrementAndGet();
            return awaitThenReturn(loading, release, List.of());
        }));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        startWaiters(WeatherCaches.MAX_WAITERS, 60, 127);

        // when
        assertThatThrownBy(() -> caches.shortTerm(BASE, 60, 127, () -> {
                    calls.incrementAndGet();
                    return List.of();
                }))
                .isInstanceOf(WeatherCacheBusyException.class);
        release.countDown();

        // then
        assertThat(calls).hasValue(1);
    }

    private List<Future<List<ShortTermForecast>>> startWaiters(int count, int nx, int ny) {
        List<Thread> threads = new CopyOnWriteArrayList<>();
        List<Future<List<ShortTermForecast>>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(executor.submit(() -> {
                threads.add(Thread.currentThread());
                return caches.shortTerm(BASE, nx, ny, () -> {
                    throw new AssertionError("기다리는 요청이 호출을 시작하면 안 된다");
                });
            }));
        }
        await().atMost(Duration.ofSeconds(5))
                .until(() -> threads.size() == count
                        && threads.stream().allMatch(thread -> thread.getState() == Thread.State.WAITING));
        return results;
    }

    private <T> List<Future<T>> runConcurrently(CountDownLatch loading, CountDownLatch release, Callable<T> task)
            throws InterruptedException {
        List<Future<T>> results = new ArrayList<>();
        results.add(executor.submit(task));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        List<Thread> waiters = new CopyOnWriteArrayList<>();
        for (int i = 0; i < WAITERS; i++) {
            results.add(executor.submit(() -> {
                waiters.add(Thread.currentThread());
                return task.call();
            }));
        }
        await().atMost(Duration.ofSeconds(5))
                .until(() -> waiters.size() == WAITERS
                        && waiters.stream().allMatch(thread -> thread.getState() == Thread.State.WAITING));
        release.countDown();
        return results;
    }

    private static <T> T awaitThenReturn(CountDownLatch loading, CountDownLatch release, T value) {
        loading.countDown();
        try {
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return value;
    }
}
