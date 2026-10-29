package com.moewadeul.memory.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.ReportIntake;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryErrorCode;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.report.MemoryReport;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryReportService {

    private static final ReportIntake REPORT_INTAKE =
            new ReportIntake("uk_memory_report_memory_reporter", MemoryErrorCode.MEMORY_ALREADY_REPORTED);

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final Clock clock;

    @Transactional
    public void report(long memberId, long memoryId, MemoryReportCommand command) {
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .filter(Memory::isVisibleToOthers)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = clock.instant();
        long unreviewedReportCount = REPORT_INTAKE.accept(
                () -> memoryReportRepository.existsByMemoryIdAndReporterId(memoryId, memberId),
                () -> memoryReportRepository.saveAndFlush(
                        MemoryReport.of(memoryId, memberId, command.reason(), command.content(), now)),
                () -> memoryReportRepository.countByMemoryIdAndReviewedAtIsNull(memoryId));
        memory.receiveReport(unreviewedReportCount, now);
    }
}
