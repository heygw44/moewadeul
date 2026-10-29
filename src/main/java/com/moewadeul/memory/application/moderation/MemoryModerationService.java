package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.moderation.AdminModeration;
import com.moewadeul.common.moderation.ModerationResult;
import com.moewadeul.common.moderation.ModerationTarget;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryModerationService {

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final AdminModeration adminModeration;

    private final ModerationTarget<Memory> target = new ModerationTarget<>() {

        @Override
        public AdminAuditAction hideAction() {
            return AdminAuditAction.MEMORY_HIDE;
        }

        @Override
        public AdminAuditAction restoreAction() {
            return AdminAuditAction.MEMORY_RESTORE;
        }

        @Override
        public AdminAuditTargetType auditTargetType() {
            return AdminAuditTargetType.MEMORY;
        }

        @Override
        public Memory lock(long memoryId) {
            return memoryRepository.findByIdForUpdate(memoryId).orElseThrow(BusinessException::notFound);
        }

        @Override
        public boolean canBeHidden(Memory memory) {
            return memory.isPublic();
        }

        @Override
        public int markReviewed(long memoryId, Instant now) {
            return memoryReportRepository.markReviewed(memoryId, now);
        }
    };

    @Transactional
    public ModerationResult hide(long memoryId, long adminId) {
        return adminModeration.hide(target, memoryId, adminId);
    }

    @Transactional
    public ModerationResult restore(long memoryId, long adminId) {
        return adminModeration.restore(target, memoryId, adminId);
    }
}
