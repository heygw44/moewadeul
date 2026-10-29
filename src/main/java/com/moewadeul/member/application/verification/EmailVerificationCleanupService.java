package com.moewadeul.member.application.verification;

import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.infra.verification.EmailVerificationCleanupMapper;
import com.moewadeul.member.infra.verification.EmailVerificationIpLockMapper;
import com.moewadeul.member.infra.verification.SignupDuplicateAttemptMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationCleanupService {

    static final Duration IP_LOCK_ROW_IDLE_LIMIT = Duration.ofHours(24);

    static final int BATCH_SIZE = 1000;

    private final EmailVerificationCleanupMapper cleanupMapper;
    private final EmailVerificationIpLockMapper ipLockMapper;
    private final SignupDuplicateAttemptMapper signupDuplicateAttemptMapper;
    private final Clock clock;

    public int deleteExpired() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(EmailVerificationPolicy.RECORD_RETENTION);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deleteOlderThan(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("email verification deleted count={}", deletedTotal);
        deleteIdleIpLockRows(now.minus(IP_LOCK_ROW_IDLE_LIMIT));
        deleteDuplicateSignupAttempts(now.minus(EmailVerificationPolicy.IP_WINDOW));
        return deletedTotal;
    }

    private void deleteIdleIpLockRows(Instant cutoff) {
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = ipLockMapper.deleteIdleBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("email verification ip lock rows deleted count={}", deletedTotal);
    }

    private void deleteDuplicateSignupAttempts(Instant cutoff) {
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = signupDuplicateAttemptMapper.deleteBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("signup duplicate attempts deleted count={}", deletedTotal);
    }
}
