package com.moewadeul.trust.application.identity;

import com.moewadeul.trust.infra.identity.CiRetentionCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CiRetentionCleanupService {

    static final int BATCH_SIZE = 1000;

    private final CiRetentionCleanupMapper cleanupMapper;
    private final Clock clock;

    public int deleteRetentionExpired() {
        Instant now = Instant.now(clock);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deleteRetentionExpired(now, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("identity verification deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
