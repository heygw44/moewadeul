package com.moewadeul.community.api.report;

import com.moewadeul.community.application.report.CommunityReportCommand;
import com.moewadeul.community.domain.report.CommunityReport;
import com.moewadeul.community.domain.report.CommunityReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommunityReportRequest(
        @Schema(description = "신고 사유.") @NotNull(message = "신고 사유를 입력해 주세요.")
        CommunityReportReason reason,

        @Size(max = CommunityReport.CONTENT_MAX_LENGTH, message = "신고 내용은 1000자 이하여야 해요.")
        String content) {

    CommunityReportCommand toCommand() {
        return new CommunityReportCommand(reason, content);
    }
}
