package com.moewadeul.memory.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryErrorCode;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.report.MemoryReport;
import com.moewadeul.memory.domain.report.MemoryReportReason;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryReportService {

    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final Clock clock;

    @Transactional
    public void report(long memberId, long memoryId, MemoryReportCommand command) {
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .filter(Memory::isVisibleToOthers)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        MemoryReportReason reason = parseReason(command.reason());
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
            throw alreadyReported();
        }
        if (memoryReportRepository.countByMemoryIdAndReviewedAtIsNull(memoryId) >= PENDING_REVIEW_REPORT_THRESHOLD) {
            memory.markPendingReview(now);
        }
    }

    private static MemoryReportReason parseReason(String value) {
        return Arrays.stream(MemoryReportReason.values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.INVALID_INPUT, "신고 사유는 " + allowedReasons() + " 중 하나여야 해요."));
    }

    private static String allowedReasons() {
        return String.join(
                ", ", Arrays.stream(MemoryReportReason.values()).map(Enum::name).toList());
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(MemoryErrorCode.MEMORY_ALREADY_REPORTED);
    }
}
