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

/**
 * 관리자가 장소를 숨기거나 복구한다.
 *
 * <p>두 메서드 모두 첫 쿼리로 장소 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 박지 신고도 같은 행을 먼저 잠그므로, 복구와
 * 신고가 줄을 선다. 그래서 복구 전에 커밋된 신고는 검토를 마친 것으로 표시되고, 복구 뒤의 신고는 새로 센다. 같은 장소를 동시에 숨기면 뒤
 * 요청은 잠금을 기다린 뒤 이미 숨긴 상태를 보고 거부된다. 장소가 없거나 제보자가 지운 장소이면 존재를 드러내지 않으려고 NOT_FOUND로
 * 거부하고, 상태 전이를 할 수 없으면 SPOT_INVALID_STATE로 거부한다.
 *
 * <p>공공데이터 장소는 원천 삭제 표시(source_removed_at)로 동기화의 숨김을 조정한다. 동기화는 ACTIVE이면서 표시가 없는 장소만 원천 삭제로
 * 숨기고, 표시가 있는 장소는 원천에 다시 나타날 때만 되살린다. 그래서 관리자가 숨길 때는 표시를 지운다. 표시를 남겨 두면 그 장소가 원천에 다시
 * 나타났을 때 동기화가 관리자의 숨김을 되돌린다. 반대로 복구할 때는 표시가 없으면 지금 시각으로 남긴다. 표시가 있는 ACTIVE 장소는 관리자가 다시
 * 공개한 장소로 보고 동기화가 숨기지 않기 때문이다. 표시가 없으면 원천이 다음 날에도 삭제로 알려 올 때 동기화가 그 장소를 다시 숨긴다. 이미 있는
 * 표시는 그대로 둔다. 두 메서드 모두 장소 행을 잠근 뒤에 표시 행을 바꾼다. 동기화의 원천 삭제도 같은 순서로 잠그므로, 두 작업이 같은 장소에
 * 동시에 와도 한쪽이 다른 쪽의 커밋을 기다릴 뿐 데드락이 나지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotModerationService {

    private final SpotRepository spotRepository;
    private final BakjiReportRepository bakjiReportRepository;
    private final PublicSpotMapper publicSpotMapper;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /** 호출하면 ACTIVE나 검토 대기 장소를 HIDDEN으로 바꾸고 감사 로그를 남긴다. 장소 종류는 묻지 않는다. */
    @Transactional
    public SpotStatusResult hide(long spotId, long adminId) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
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

    /**
     * 호출하면 검토 대기나 HIDDEN 장소를 ACTIVE로 바꾸고, 그 장소의 검토 전 신고를 모두 검토를 마친 것으로 표시한 뒤 감사 로그를 남긴다. 공공데이터
     * 장소에 원천 삭제 표시가 없으면 지금 시각으로 남기고, 있으면 그대로 둔다.
     */
    @Transactional
    public SpotStatusResult restore(long spotId, long adminId) {
        Spot spot = lockSpot(spotId);
        SpotStatus before = spot.getStatus();
        Instant now = clock.instant();
        spot.restore(now);
        markSourceRemovedIfPublicData(spot, now);
        // 이 쿼리가 실행되기 전에 JPA가 위의 상태 변경을 플러시한다. 신고 엔티티는 읽지 않아서 영속성 컨텍스트를 비울 필요가 없다.
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

    // MyBatis로 갱신하는 public_spot_detail 행은 이 트랜잭션에서 엔티티로 읽지 않는다. 장소 엔티티의 상태 변경은 커밋 때 플러시된다.
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
