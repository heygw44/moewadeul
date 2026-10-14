package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 추억 기록을 숨기거나 복구한다.
 *
 * <p>두 메서드는 첫 쿼리로 기록 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 신고도 같은 행을 먼저 잠그므로, 복구와 신고가 줄을 선다. 그래서
 * 복구 전에 커밋된 신고는 검토를 마친 것으로 표시되고, 복구 뒤의 신고는 새로 센다. 같은 기록을 동시에 숨기면 뒤 요청은 잠금을 기다린 뒤 이미 숨긴
 * 상태를 보고 MEMORY_INVALID_STATE로 거부된다.
 *
 * <p>관리자도 비공개 기록은 보지 못한다. 그래서 비공개 기록을 숨기려 하면 기록이 없는 것과 같은 NOT_FOUND로 거부한다. 복구는 공개 범위를 보지
 * 않는다. 숨긴 뒤 작성자가 비공개로 바꾼 기록도 관리자가 숨김을 되돌릴 수 있어야 해서다. 복구해도 비공개 기록은 여전히 작성자만 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryModerationService {

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /** 호출하면 ACTIVE나 검토 대기인 공개 기록을 HIDDEN으로 바꾸고 감사 로그를 남긴다. */
    @Transactional
    public MemoryModerationResult hide(long memoryId, long adminId) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .filter(Memory::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        MemoryStatus before = memory.getStatus();
        memory.hide(clock.instant());
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.MEMORY_HIDE,
                AdminAuditTargetType.MEMORY,
                memoryId,
                new MemoryAuditDetails.Hide(before.name(), memory.getStatus().name()));
        log.info("memory hidden memoryId={} adminId={}", memoryId, adminId);
        return new MemoryModerationResult(memoryId, memory.getStatus().name());
    }

    /** 호출하면 검토 대기나 HIDDEN인 기록을 ACTIVE로 바꾸고, 그 기록의 검토 전 신고를 모두 검토를 마친 것으로 표시한 뒤 감사 로그를 남긴다. */
    @Transactional
    public MemoryModerationResult restore(long memoryId, long adminId) {
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        MemoryStatus before = memory.getStatus();
        Instant now = clock.instant();
        memory.restore(now);
        // 이 쿼리가 실행되기 전에 JPA가 위의 상태 변경을 플러시한다. 신고 엔티티는 읽지 않아서 영속성 컨텍스트를 비울 필요가 없다.
        int reviewed = memoryReportRepository.markReviewed(memoryId, now);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.MEMORY_RESTORE,
                AdminAuditTargetType.MEMORY,
                memoryId,
                new MemoryAuditDetails.Restore(before.name(), memory.getStatus().name(), reviewed));
        log.info("memory restored memoryId={} adminId={} reviewedReportCount={}", memoryId, adminId, reviewed);
        return new MemoryModerationResult(memoryId, memory.getStatus().name());
    }
}
