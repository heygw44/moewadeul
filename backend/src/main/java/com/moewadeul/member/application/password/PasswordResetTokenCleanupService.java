package com.moewadeul.member.application.password;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.member.domain.password.PasswordResetPolicy;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetTokenCleanupService {

    static final int BATCH_SIZE = 1000;

    private final PasswordResetTokenMapper tokenMapper;
    private final Clock clock;

    public int deleteExpiredTokens() {
        Instant cutoff = Instant.now(clock).minus(PasswordResetPolicy.TOKEN_RETENTION_AFTER_EXPIRY);
        int deletedTotal =
                BatchDeletion.untilDrained(BATCH_SIZE, size -> tokenMapper.deleteExpiredBefore(cutoff, size));
        log.info("password reset token deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
