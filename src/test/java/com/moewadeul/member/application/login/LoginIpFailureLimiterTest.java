package com.moewadeul.member.application.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.MemberErrorCode;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginIpFailureLimiterTest {

    private static final String IP = "203.0.113.7";

    private final MutableClock clock = MutableClock.atDefaultInstant();
    private final LoginIpFailureLimiter limiter = new LoginIpFailureLimiter(clock);

    @Test
    @DisplayName("한 IP에서 20번 실패하는 동안은 막지 않고 21번째 시도부터 3초 뒤를 알리며 막는다")
    void rejectsAfterTwentyFailures() {
        fail(IP, 20);

        assertThatThrownBy(() -> limiter.rejectIfExhausted(IP))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.LOGIN_LOCKED);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    @Test
    @DisplayName("19번 실패한 뒤에는 한 번 더 시도할 수 있다")
    void allowsAttemptAfterNineteenFailures() {
        fail(IP, 19);

        assertThatCode(() -> limiter.rejectIfExhausted(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("막힌 동안 시간이 흐르면 남은 시간이 줄고, 3초가 지나면 한 번 더 시도할 수 있다")
    void refillsOneTokenEveryThreeSeconds() {
        fail(IP, 20);

        clock.advance(Duration.ofMillis(1500));
        assertThatThrownBy(() -> limiter.rejectIfExhausted(IP))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMillis(1500)));

        clock.advance(Duration.ofMillis(1500));
        assertThatCode(() -> limiter.rejectIfExhausted(IP)).doesNotThrowAnyException();
        limiter.recordFailure(IP);
        assertThatThrownBy(() -> limiter.rejectIfExhausted(IP)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("막힌 채로 계속 실패를 기록해도 기다릴 시간은 3초를 넘지 않는다")
    void retryAfterIsBoundedWhenFailuresKeepArriving() {
        fail(IP, 20);
        fail(IP, 50);

        assertThatThrownBy(() -> limiter.rejectIfExhausted(IP))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(3)));
    }

    @Test
    @DisplayName("60초가 지나면 20번을 다시 실패할 수 있다")
    void fullyRefillsAfterOneMinute() {
        fail(IP, 20);

        clock.advance(Duration.ofSeconds(60));

        fail(IP, 19);
        assertThatCode(() -> limiter.rejectIfExhausted(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IP마다 따로 센다")
    void countsEachIpSeparately() {
        fail(IP, 20);

        assertThatCode(() -> limiter.rejectIfExhausted("203.0.113.8")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 IP에서 동시에 실패해도 하나도 빠지지 않고 센다")
    void countsConcurrentFailuresExactly() throws Exception {
        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            IntStream.range(0, threads)
                    .forEach(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        limiter.recordFailure(IP);
                        return null;
                    }));
            ready.await();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
        }

        assertThatThrownBy(() -> limiter.rejectIfExhausted(IP)).isInstanceOf(RateLimitedException.class);
        clock.advance(Duration.ofSeconds(3));
        assertThatCode(() -> limiter.rejectIfExhausted(IP)).doesNotThrowAnyException();
        limiter.recordFailure(IP);
        assertThatThrownBy(() -> limiter.rejectIfExhausted(IP)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("IP가 비어 있으면 거부한다")
    void rejectsBlankIp() {
        assertThatThrownBy(() -> limiter.rejectIfExhausted(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.recordFailure(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void fail(String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(ip);
        }
    }
}
