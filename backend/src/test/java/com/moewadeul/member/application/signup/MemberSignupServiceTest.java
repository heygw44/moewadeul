package com.moewadeul.member.application.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.application.verification.EmailSendIpLimiter;
import com.moewadeul.member.domain.MemberErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberSignupServiceTest {

    private static final int REQUESTS = 8;

    private final MemberSignupApplier applier = mock(MemberSignupApplier.class);
    private final EmailSendIpLimiter limiter = mock(EmailSendIpLimiter.class);
    private final AtomicInteger hashing = new AtomicInteger();
    private final AtomicInteger maxHashing = new AtomicInteger();
    private final AtomicInteger hashes = new AtomicInteger();
    private final PasswordEncoder slowEncoder = mock(PasswordEncoder.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
    private final MemberSignupService service = new MemberSignupService(applier, limiter, slowEncoder);

    MemberSignupServiceTest() {
        when(slowEncoder.encode(anyString())).thenAnswer(invocation -> {
            maxHashing.accumulateAndGet(hashing.incrementAndGet(), Math::max);
            hashes.incrementAndGet();
            Thread.sleep(50);
            hashing.decrementAndGet();
            return "hash";
        });
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("[F-01] 가입이 한꺼번에 몰려도 BCrypt는 한 번에 둘까지만 계산한다")
    void hashesAtMostTwoAtOnce() throws Exception {
        List<Throwable> failures = signUpTogether();

        assertThat(failures).isEmpty();
        assertThat(hashes.get()).isEqualTo(REQUESTS);
        assertThat(maxHashing.get()).isEqualTo(MemberSignupService.MAX_CONCURRENT_SIGNUPS);
    }

    @Test
    @DisplayName("[F-01][EV-03] 줄 서 있던 가입은 차례가 온 뒤 IP 한도를 보므로, 그사이 한도가 차면 해시를 계산하지 않고 SIGNUP_LIMITED다")
    void waitingSignupsSeeLimitFilledWhileQueued() throws Exception {
        AtomicInteger committed = new AtomicInteger();
        when(applier.signUp(any(), any(), anyString(), anyString())).thenAnswer(invocation -> {
            committed.incrementAndGet();
            return null;
        });
        doAnswer(invocation -> {
                    if (committed.get() >= MemberSignupService.MAX_CONCURRENT_SIGNUPS) {
                        throw new RateLimitedException(MemberErrorCode.SIGNUP_LIMITED, Duration.ofMinutes(1));
                    }
                    return null;
                })
                .when(applier)
                .rejectBeforeHashing(any(), any());

        List<Throwable> failures = signUpTogether();

        assertThat(hashes.get()).isEqualTo(MemberSignupService.MAX_CONCURRENT_SIGNUPS);
        assertThat(failures)
                .hasSize(REQUESTS - MemberSignupService.MAX_CONCURRENT_SIGNUPS)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(RateLimitedException.class));
    }

    private List<Throwable> signUpTogether() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            SignupCommand command =
                    new SignupCommand("user" + i + "@example.com", "Passw0rd!xyz", "nick" + i, "203.0.113.7");
            futures.add(executor.submit(() -> {
                start.await();
                return service.signUp(command);
            }));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        return failures;
    }
}
