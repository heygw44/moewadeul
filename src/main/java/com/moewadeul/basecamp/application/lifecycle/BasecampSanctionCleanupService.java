package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.RemovalCause;
import com.moewadeul.basecamp.infra.lifecycle.BasecampSanctionCleanupMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasecampSanctionCleanupService {

    private final BasecampSanctionCleanupMapper cleanupMapper;
    private final BasecampSanctionCleanupApplier applier;

    public void cleanUp(long memberId, RemovalCause cause) {
        List<Long> basecampIds = cleanupMapper.selectInProgressBasecampIds(memberId);
        List<Long> failedBasecampIds = new ArrayList<>();
        for (long basecampId : basecampIds) {
            try {
                applier.cleanUp(basecampId, memberId, cause);
            } catch (RuntimeException e) {
                log.error("basecamp cleanup failed cause={} memberId={} basecampId={}", cause, memberId, basecampId, e);
                failedBasecampIds.add(basecampId);
            }
        }
        log.info(
                "basecamp cleanup cause={} memberId={} targets={} failed={}",
                cause,
                memberId,
                basecampIds.size(),
                failedBasecampIds.size());
        if (!failedBasecampIds.isEmpty()) {
            throw new IllegalStateException(
                    "베이스캠프 정리에 실패한 베이스캠프가 있습니다. cause=" + cause + " basecampIds=" + failedBasecampIds);
        }
    }
}
