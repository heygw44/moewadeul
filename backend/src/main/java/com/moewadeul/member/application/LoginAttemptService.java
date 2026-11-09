package com.moewadeul.member.application;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.login.LoginFailure;
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
    public void rejectIfLocked(Long memberId, String attemptedEmail, String ip) {
        rejectIfLocked(memberId, attemptedEmail, ip, Instant.now(clock));
    }

    @Transactional
    public void recordAttemptUnderEmailLock(Long memberId, String attemptedEmail, String ip, boolean passwordMatched) {
        Instant now = Instant.now(clock);
        attemptLockMapper.lock(LoginLockout.attemptKey(memberId, attemptedEmail), now);
        rejectIfLocked(memberId, attemptedEmail, ip, now);
        if (!passwordMatched) {
            loginHistoryRepository.save(LoginHistory.failure(memberId, attemptedEmail, ip, now));
        }
    }

    @Transactional
    public void recordSuccess(long memberId, String attemptedEmail, String ip) {
        loginHistoryRepository.save(LoginHistory.success(memberId, attemptedEmail, ip, Instant.now(clock)));
    }

    private void rejectIfLocked(Long memberId, String attemptedEmail, String ip, Instant now) {
        Instant windowStart = LoginLockout.windowStart(now);
        List<LoginFailure> failures = memberId != null
                ? loginHistoryRepository.findMemberFailuresSinceLastSuccess(memberId, windowStart)
                : loginHistoryRepository.findFailuresSinceLastSuccess(attemptedEmail, windowStart);
        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, ip, now);
        if (lockedUntil.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, Duration.between(now, lockedUntil.get()));
        }
    }
}
