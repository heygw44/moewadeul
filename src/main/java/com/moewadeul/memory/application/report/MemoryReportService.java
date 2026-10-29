package com.moewadeul.memory.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryErrorCode;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.report.MemoryReport;
import com.moewadeul.memory.domain.report.MemoryReportReason;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryReportService {

    private static final String REPORT_UNIQUE_CONSTRAINT = "uk_memory_report_memory_reporter";
    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;
    private static final String REASON_MESSAGE = "신고 사유는 " + EnumParser.names(MemoryReportReason.class) + " 중 하나여야 해요.";

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final Clock clock;

    @Transactional
    public void report(long memberId, long memoryId, MemoryReportCommand command) {
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .filter(Memory::isVisibleToOthers)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        MemoryReportReason reason = EnumParser.parse(MemoryReportReason.class, command.reason(), REASON_MESSAGE);
        String content = command.content();
        if (content != null && content.length() > MemoryReport.CONTENT_MAX_LENGTH) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "신고 내용은 " + MemoryReport.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
        if (memoryReportRepository.existsByMemoryIdAndReporterId(memoryId, memberId)) {
            throw alreadyReported();
        }
        Instant now = clock.instant();
        try {
            memoryReportRepository.saveAndFlush(MemoryReport.of(memoryId, memberId, reason, content, now));
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolations.translate(e, REPORT_UNIQUE_CONSTRAINT, MemoryErrorCode.MEMORY_ALREADY_REPORTED);
        }
        if (memoryReportRepository.countByMemoryIdAndReviewedAtIsNull(memoryId) >= PENDING_REVIEW_REPORT_THRESHOLD) {
            memory.markPendingReview(now);
        }
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(MemoryErrorCode.MEMORY_ALREADY_REPORTED);
    }
}
