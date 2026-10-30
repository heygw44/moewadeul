package com.moewadeul.member.application.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.application.LoginAttemptService;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.login.LoginLockout;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberLoginServiceIpReservationTest {

    private static final String IP = "203.0.113.7";
    private static final int CAPACITY = LoginLockout.IP_FAILURE_CAPACITY;

    private final MutableClock clock = MutableClock.atDefaultInstant();
    private final LoginIpFailureLimiter limiter = new LoginIpFailureLimiter(clock);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AtomicInteger matchesCalls = new AtomicInteger();
    private final MemberLoginService service;

    MemberLoginServiceIpReservationTest() {
        when(passwordEncoder.encode(anyString())).thenReturn("decoy-hash");
        when(passwordEncoder.matches(anyString(), anyString())).thenAnswer(invocation -> {
            matchesCalls.incrementAndGet();
            return false;
        });
        when(memberRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(loginAttemptService.findRetryAfter(any(), anyString())).thenReturn(Optional.empty());
        service = new MemberLoginService(
                memberRepository,
                loginAttemptService,
                passwordEncoder,
                mock(MemberSuspensionService.class),
                mock(MemberSessions.class),
                limiter,
                clock);
    }

    @Test
    @DisplayName("한 IP에서 100개가 동시에 실패하는 로그인을 해도 BCrypt는 정확히 20번만 돈다")
    void concurrentFailedLoginsRunBcryptExactlyCapacityTimes() throws Exception {
        int requests = 100;
        CountDownLatch bcryptGate = new CountDownLatch(1);
        doAnswer(invocation -> {
                    matchesCalls.incrementAndGet();
                    assertThat(bcryptGate.await(10, TimeUnit.SECONDS)).isTrue();
                    return false;
                })
                .when(passwordEncoder)
                .matches(anyString(), anyString());
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger loginFailed = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            Future<?>[] results = IntStream.range(0, requests)
                    .mapToObj(i -> pool.submit(() -> {
                        go.await();
                        try {
                            service.login(new LoginCommand("nobody" + i + "@example.com", "wrong-pass", IP));
                        } catch (RateLimitedException e) {
                            rejected.incrementAndGet();
                        } catch (BusinessException e) {
                            loginFailed.incrementAndGet();
                        }
                        return null;
                    }))
                    .toArray(Future<?>[]::new);
            go.countDown();
            awaitUntil(() -> rejected.get() == requests - CAPACITY);
            assertThat(matchesCalls.get()).isEqualTo(CAPACITY);

            bcryptGate.countDown();
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(matchesCalls.get()).isEqualTo(CAPACITY);
        assertThat(loginFailed.get()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("BCrypt 뒤 이메일 잠금 쓰기에서 429가 나도 토큰은 돌려주지 않는다")
    void rateLimitedAfterBcryptKeepsToken() {
        doThrow(new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, Duration.ofSeconds(30)))
                .when(loginAttemptService)
                .recordAttemptUnderEmailLock(any(), anyString(), anyString(), anyBoolean());

        for (int i = 0; i < CAPACITY; i++) {
            assertThatThrownBy(() -> service.login(command())).isInstanceOf(RateLimitedException.class);
        }

        assertThat(matchesCalls.get()).isEqualTo(CAPACITY);
        assertThatThrownBy(() -> service.login(command())).isInstanceOf(RateLimitedException.class);
        assertThat(matchesCalls.get()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("이메일별 잠금으로 BCrypt 전에 거절된 요청은 토큰을 돌려준다")
    void emailLockoutBeforeBcryptRefundsToken() {
        when(loginAttemptService.findRetryAfter(any(), anyString())).thenReturn(Optional.of(Duration.ofSeconds(30)));

        for (int i = 0; i < CAPACITY * 2; i++) {
            assertThatThrownBy(() -> service.login(command())).isInstanceOf(RateLimitedException.class);
        }

        assertThat(matchesCalls.get()).isZero();
        assertThatCode(() -> limiter.reserve(IP)).doesNotThrowAnyException();
    }

    private static LoginCommand command() {
        return new LoginCommand("nobody@example.com", "wrong-pass", IP);
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(5);
        }
    }
}
