package com.moewadeul.spot.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.AdminModeration;
import com.moewadeul.common.moderation.ModerationResult;
import com.moewadeul.common.moderation.ModerationTarget;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.bakji.BakjiReportRepository;
import com.moewadeul.spot.infra.publicspot.PublicSpotMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotModerationService {

    private final SpotRepository spotRepository;
    private final BakjiReportRepository bakjiReportRepository;
    private final PublicSpotMapper publicSpotMapper;
    private final AdminModeration adminModeration;

    private final ModerationTarget<Spot> target = new ModerationTarget<>() {

        @Override
        public AdminAuditAction hideAction() {
            return AdminAuditAction.SPOT_HIDE;
        }

        @Override
        public AdminAuditAction restoreAction() {
            return AdminAuditAction.SPOT_RESTORE;
        }

        @Override
        public String auditTargetType() {
            return AdminAuditTargetType.SPOT;
        }

        @Override
        public Spot lock(long spotId) {
            return spotRepository
                    .findByIdForUpdate(spotId)
                    .filter(spot -> !spot.isDeleted())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        }

        @Override
        public int markReviewed(long spotId, Instant now) {
            return bakjiReportRepository.markReviewed(spotId, now);
        }

        @Override
        public void afterHide(Spot spot, Instant now) {
            if (spot.isPublicData()) {
                publicSpotMapper.clearSourceRemoved(List.of(spot.getId()), now);
            }
        }

        @Override
        public void afterRestore(Spot spot, Instant now) {
            if (spot.isPublicData()) {
                publicSpotMapper.markSourceRemovedIfAbsent(List.of(spot.getId()), now);
            }
        }

        @Override
        public Object auditDetail(Spot spot, Object transition) {
            return new SpotAuditDetail(spot.getType().name(), transition);
        }
    };

    @Transactional
    public ModerationResult hide(long spotId, long adminId) {
        return adminModeration.hide(target, spotId, adminId);
    }

    @Transactional
    public ModerationResult restore(long spotId, long adminId) {
        return adminModeration.restore(target, spotId, adminId);
    }
}
