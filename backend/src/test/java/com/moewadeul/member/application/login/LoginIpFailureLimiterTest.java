package com.moewadeul.member.application.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.login.LoginLockout;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

        assertThatThrownBy(() -> limiter.reserve(IP)).isInstanceOfSatisfying(RateLimitedException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.LOGIN_LOCKED);
            assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(3));
        });
    }

    @Test
    @DisplayName("19번 실패한 뒤에는 한 번 더 시도할 수 있다")
    void allowsAttemptAfterNineteenFailures() {
        fail(IP, 19);

        assertThatCode(() -> limiter.reserve(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 IP에서 동시에 100번 예약하면 정확히 20번만 통과하고 나머지는 거절한다")
    void concurrentReservationsPassExactlyCapacity() throws Exception {
        int threads = 100;
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            IntStream.range(0, threads)
                    .forEach(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            limiter.reserve(IP);
                            passed.incrementAndGet();
                        } catch (RateLimitedException e) {
                            rejected.incrementAndGet();
                        }
                        return null;
                    }));
            ready.await();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(passed.get()).isEqualTo(LoginLockout.IP_FAILURE_CAPACITY);
        assertThat(rejected.get()).isEqualTo(threads - LoginLockout.IP_FAILURE_CAPACITY);
    }

    @Test
    @DisplayName("돌려주면 토큰이 정확히 하나만 되살아난다")
    void refundRestoresExactlyOneToken() {
        fail(IP, 20);

        limiter.refund(IP);

        assertThatCode(() -> limiter.reserve(IP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.reserve(IP)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("기록이 없거나 이미 가득 찬 IP에 돌려줘도 한도가 늘지 않는다")
    void refundOnAbsentOrFullIpIsHarmless() {
        limiter.refund(IP);
        limiter.reserve(IP);
        limiter.refund(IP);
        limiter.refund(IP);

        fail(IP, 20);
        assertThatThrownBy(() -> limiter.reserve(IP)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("막힌 동안 시간이 흐르면 남은 시간이 줄고, 3초가 지나면 한 번 더 시도할 수 있다")
    void refillsOneTokenEveryThreeSeconds() {
        fail(IP, 20);

        clock.advance(Duration.ofMillis(1500));
        assertThatThrownBy(() -> limiter.reserve(IP))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMillis(1500)));

        clock.advance(Duration.ofMillis(1500));
        assertThatCode(() -> limiter.reserve(IP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.reserve(IP)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("막힌 채로 계속 예약해도 기다릴 시간은 3초를 넘지 않는다")
    void retryAfterIsBoundedWhenFailuresKeepArriving() {
        fail(IP, 20);
        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> limiter.reserve(IP)).isInstanceOf(RateLimitedException.class);
        }

        assertThatThrownBy(() -> limiter.reserve(IP))
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
        assertThatCode(() -> limiter.reserve(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IP마다 따로 센다")
    void countsEachIpSeparately() {
        fail(IP, 20);

        assertThatCode(() -> limiter.reserve("203.0.113.8")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IP가 비어 있으면 거부한다")
    void rejectsBlankIp() {
        assertThatThrownBy(() -> limiter.reserve(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.reserve(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void fail(String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.reserve(ip);
        }
    }
}
