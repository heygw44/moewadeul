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

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryModerationService {

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    public MemoryModerationResult hide(long memoryId, long adminId) {
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

    @Transactional
    public MemoryModerationResult restore(long memoryId, long adminId) {
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        MemoryStatus before = memory.getStatus();
        Instant now = clock.instant();
        memory.restore(now);
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
