package com.moewadeul.member.application;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.login.LoginHistory;
import com.moewadeul.member.domain.login.LoginHistoryRepository;
import com.moewadeul.member.domain.login.LoginLockout;
import com.moewadeul.member.infra.login.LoginAttemptLockMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final LoginAttemptLockMapper attemptLockMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<Duration> findRetryAfter(String attemptedEmail) {
        return retryAfter(attemptedEmail, Instant.now(clock));
    }

    @Transactional
    public void settle(Long memberId, String attemptedEmail, String ip, boolean passwordMatched) {
        Instant now = Instant.now(clock);
        attemptLockMapper.lock(LoginLockout.attemptKey(attemptedEmail), now);
        Optional<Duration> retryAfter = retryAfter(attemptedEmail, now);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
        if (!passwordMatched) {
            loginHistoryRepository.save(LoginHistory.failure(memberId, attemptedEmail, ip, now));
        }
    }

    @Transactional
    public void recordSuccess(long memberId, String attemptedEmail, String ip) {
        loginHistoryRepository.save(LoginHistory.success(memberId, attemptedEmail, ip, Instant.now(clock)));
    }

    private Optional<Duration> retryAfter(String attemptedEmail, Instant now) {
        List<Instant> failureTimes =
                loginHistoryRepository.findFailureTimesSinceLastSuccess(attemptedEmail, LoginLockout.windowStart(now));
        return LoginLockout.lockedUntil(failureTimes, now).map(lockedUntil -> Duration.between(now, lockedUntil));
    }
}
