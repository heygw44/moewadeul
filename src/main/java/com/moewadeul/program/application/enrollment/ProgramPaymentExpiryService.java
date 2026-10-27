package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import com.moewadeul.program.infra.enrollment.ProgramPaymentExpiryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPaymentExpiryService {

    static final int BATCH_SIZE = 500;

    private final ProgramPaymentExpiryMapper expiryMapper;
    private final ProgramPaymentExpiryApplier applier;
    private final Clock clock;

    public Result run() {
        Instant now = clock.instant();
        int expired = 0;
        int failed = 0;
        long afterId = 0;
        while (true) {
            List<ExpiryTarget> targets = expiryMapper.selectDueTargets(now, afterId, BATCH_SIZE);
            for (ExpiryTarget target : targets) {
                switch (expireOne(target, now)) {
                    case EXPIRED -> expired++;
                    case FAILED -> failed++;
                    case UNCHANGED -> {}
                }
            }
            if (targets.size() < BATCH_SIZE) {
                break;
            }
            afterId = targets.get(targets.size() - 1).applicationId();
        }
        log.info("program payment expiry completed expired={} failed={}", expired, failed);
        return new Result(expired, failed);
    }

    private ItemResult expireOne(ExpiryTarget target, Instant now) {
        try {
            return applier.expire(target, now) ? ItemResult.EXPIRED : ItemResult.UNCHANGED;
        } catch (RuntimeException e) {
            log.error("program payment expiry failed applicationId={}", target.applicationId(), e);
            return ItemResult.FAILED;
        }
    }

    private enum ItemResult {
        EXPIRED,
        UNCHANGED,
        FAILED
    }

    public record Result(int expired, int failed) {}
}
