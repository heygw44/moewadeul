package com.moewadeul.spot.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.domain.bakji.BakjiReportRepository;
import com.moewadeul.spot.infra.publicspot.PublicSpotMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotModerationService {

    private final SpotRepository spotRepository;
    private final BakjiReportRepository bakjiReportRepository;
    private final PublicSpotMapper publicSpotMapper;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    public SpotStatusResult hide(long spotId, long adminId) {
        Spot spot = lockSpot(spotId);
        SpotStatus before = spot.getStatus();
        Instant now = clock.instant();
        spot.hide(now);
        clearSourceRemovedIfPublicData(spot, now);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.SPOT_HIDE,
                AdminAuditTargetType.SPOT,
                spotId,
                new SpotAuditDetails.Hide(
                        spot.getType().name(), before.name(), spot.getStatus().name()));
        log.info("spot hidden spotId={} adminId={}", spotId, adminId);
        return new SpotStatusResult(spotId, spot.getStatus().name());
    }

    @Transactional
    public SpotStatusResult restore(long spotId, long adminId) {
        Spot spot = lockSpot(spotId);
        SpotStatus before = spot.getStatus();
        Instant now = clock.instant();
        spot.restore(now);
        markSourceRemovedIfPublicData(spot, now);
        int reviewedReportCount = bakjiReportRepository.markReviewed(spotId, now);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.SPOT_RESTORE,
                AdminAuditTargetType.SPOT,
                spotId,
                new SpotAuditDetails.Restore(
                        spot.getType().name(), before.name(), spot.getStatus().name(), reviewedReportCount));
        log.info("spot restored spotId={} adminId={} reviewedReportCount={}", spotId, adminId, reviewedReportCount);
        return new SpotStatusResult(spotId, spot.getStatus().name());
    }

    private Spot lockSpot(long spotId) {
        return spotRepository
                .findByIdForUpdate(spotId)
                .filter(spot -> !spot.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void clearSourceRemovedIfPublicData(Spot spot, Instant now) {
        if (spot.isPublicData()) {
            publicSpotMapper.clearSourceRemoved(List.of(spot.getId()), now);
        }
    }

    private void markSourceRemovedIfPublicData(Spot spot, Instant now) {
        if (spot.isPublicData()) {
            publicSpotMapper.markSourceRemovedIfAbsent(List.of(spot.getId()), now);
        }
    }
}
