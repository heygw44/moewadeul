package com.moewadeul.spot.api.bakji;

import com.moewadeul.spot.application.bakji.BakjiProblemReportCommand;
import com.moewadeul.spot.domain.bakji.BakjiReport;
import com.moewadeul.spot.domain.bakji.BakjiReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BakjiProblemReportRequest(
        @Schema(implementation = BakjiReportReason.class, description = "신고 사유.") @NotNull(message = "신고 사유를 골라 주세요.")
        String reason,

        @Size(max = BakjiReport.CONTENT_MAX_LENGTH, message = "신고 내용은 1000자 이하여야 해요.")
        String content) {

    BakjiProblemReportCommand toCommand() {
        return new BakjiProblemReportCommand(reason, content);
    }
}
