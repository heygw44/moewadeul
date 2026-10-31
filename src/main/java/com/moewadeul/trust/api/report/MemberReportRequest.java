package com.moewadeul.trust.api.report;

import com.moewadeul.trust.application.report.MemberReportCommand;
import com.moewadeul.trust.domain.report.MemberReport;
import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MemberReportRequest(
        @NotNull(message = "신고할 회원을 골라 주세요.") @Positive(message = "신고할 회원 ID는 양수여야 해요.")
        Long targetMemberId,

        @NotNull(message = "함께한 베이스캠프를 골라 주세요.") @Positive(message = "베이스캠프 ID는 양수여야 해요.")
        Long basecampId,

        @NotNull(message = "신고 종류를 골라 주세요.") ReportKind kind,

        @Positive(message = "동행 후기 ID는 양수여야 해요.") Long companionReviewId,

        @NotNull(message = "신고 유형을 골라 주세요.") ReportType type,

        @NotBlank(message = "신고 내용을 입력해 주세요.")
        @Size(max = MemberReport.CONTENT_MAX_LENGTH, message = "신고 내용은 1000자 이하여야 해요.")
        String content) {

    MemberReportCommand toCommand() {
        return new MemberReportCommand(targetMemberId, basecampId, kind, companionReviewId, type, content);
    }
}
