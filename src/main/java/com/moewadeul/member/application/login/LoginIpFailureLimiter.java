package com.moewadeul.member.application.login;

import static com.moewadeul.member.application.MemberGuards.requireRequestIp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.login.LoginLockout;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 이메일을 바꿔 가며 시도하면 이메일별 잠금에 걸리지 않으므로, BCrypt와 DB 쓰기 전에 IP 단위로 실패한 로그인만 센다.
 * 서버 한 대 메모리에만 있으므로 재시작하면 비고, 인스턴스를 늘리면 인스턴스마다 따로 센다.
 */
@Component
public class LoginIpFailureLimiter {

    private static final long MAX_IPS = 100_000;
    private static final Duration REFILL_INTERVAL =
            LoginLockout.IP_FAILURE_WINDOW.dividedBy(LoginLockout.IP_FAILURE_CAPACITY);

    private final Cache<String, Instant> fullAgainAt;
    private final Clock clock;

    public LoginIpFailureLimiter(Clock clock) {
        this.clock = clock;
        // 마지막 기록 뒤 한 창이 지나면 버킷은 가득 찬 상태라 지워도 같다.
        this.fullAgainAt = Caffeine.newBuilder()
                .maximumSize(MAX_IPS)
                .expireAfterWrite(LoginLockout.IP_FAILURE_WINDOW)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    public void rejectIfExhausted(String ip) {
        requireRequestIp(ip);
        Instant fullAgain = fullAgainAt.getIfPresent(ip);
        if (fullAgain == null) {
            return;
        }
        Duration untilToken = Duration.between(clock.instant(), fullAgain)
                .minus(LoginLockout.IP_FAILURE_WINDOW.minus(REFILL_INTERVAL));
        if (untilToken.isPositive()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, untilToken);
        }
    }

    public void recordFailure(String ip) {
        requireRequestIp(ip);
        Instant now = clock.instant();
        Instant fullest = now.plus(LoginLockout.IP_FAILURE_WINDOW);
        fullAgainAt.asMap().compute(ip, (key, fullAgain) -> {
            Instant base = fullAgain == null || fullAgain.isBefore(now) ? now : fullAgain;
            Instant next = base.plus(REFILL_INTERVAL);
            return next.isAfter(fullest) ? fullest : next;
        });
    }

    void clear() {
        fullAgainAt.invalidateAll();
    }
}
