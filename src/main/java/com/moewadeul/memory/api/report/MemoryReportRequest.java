package com.moewadeul.memory.api.report;

import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.memory.application.report.MemoryReportCommand;
import com.moewadeul.memory.domain.report.MemoryReport;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemoryReportRequest(
        @Schema(description = "신고 사유.") @NotNull(message = "신고 사유를 골라 주세요.")
        ContentReportReason reason,

        @Size(max = MemoryReport.CONTENT_MAX_LENGTH, message = "신고 내용은 1000자 이하여야 해요.")
        String content) {

    MemoryReportCommand toCommand() {
        return new MemoryReportCommand(reason, content);
    }
}
