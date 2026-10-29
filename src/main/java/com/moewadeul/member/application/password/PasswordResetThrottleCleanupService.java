package com.moewadeul.member.application.password;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.member.domain.password.PasswordResetRequestPolicy;
import com.moewadeul.member.infra.password.PasswordResetThrottleMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetThrottleCleanupService {

    static final int BATCH_SIZE = 1000;

    private final PasswordResetThrottleMapper throttleMapper;
    private final Clock clock;

    @Transactional
    public int deleteStaleThrottles() {
        Instant cutoff = Instant.now(clock).minus(PasswordResetRequestPolicy.STALE_AFTER);
        int deletedTotal = BatchDeletion.untilDrained(BATCH_SIZE, size -> throttleMapper.deleteStale(cutoff, size));
        log.info("password reset throttle deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
