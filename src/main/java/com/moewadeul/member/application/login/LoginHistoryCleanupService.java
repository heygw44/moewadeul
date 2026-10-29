package com.moewadeul.member.application.login;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.member.infra.login.LoginAttemptLockMapper;
import com.moewadeul.member.infra.login.LoginHistoryCleanupMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryCleanupService {

    static final Period RETENTION = Period.ofMonths(3);

    static final Duration LOCK_ROW_IDLE_LIMIT = Duration.ofHours(24);

    static final int BATCH_SIZE = 1000;

    private final LoginHistoryCleanupMapper cleanupMapper;
    private final LoginAttemptLockMapper attemptLockMapper;
    private final Clock clock;

    public int deleteExpired() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.atZone(ZoneOffset.UTC).minus(RETENTION).toInstant();
        int deletedTotal = BatchDeletion.untilDrained(BATCH_SIZE, size -> cleanupMapper.deleteOlderThan(cutoff, size));
        log.info("login history deleted count={}", deletedTotal);
        deleteIdleLockRows(now.minus(LOCK_ROW_IDLE_LIMIT));
        return deletedTotal;
    }

    private void deleteIdleLockRows(Instant cutoff) {
        int deletedTotal =
                BatchDeletion.untilDrained(BATCH_SIZE, size -> attemptLockMapper.deleteIdleBefore(cutoff, size));
        log.info("login attempt lock rows deleted count={}", deletedTotal);
    }
}
