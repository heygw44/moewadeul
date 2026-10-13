package com.moewadeul.memory.api.report;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.memory.application.report.MemoryReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MemoryReportController {

    private final MemoryReportService memoryReportService;

    MemoryReportController(MemoryReportService memoryReportService) {
        this.memoryReportService = memoryReportService;
    }

    @Operation(
            summary = "공개 추억 기록 신고",
            description = "이메일 인증을 마친 회원이 공개 기록을 신고하고, 201로 응답하며 본문은 없다. "
                    + "reason은 SPAM, ABUSE, ILLEGAL_CAMPING, PRIVACY, MONEY_SCAM, OTHER 중 하나이고 모르는 값은 400 INVALID_INPUT이다. "
                    + "content는 없어도 되고 1,000자 이하다. "
                    + "한 회원은 같은 기록을 한 번만 신고할 수 있고, 이미 신고했으면 409 MEMORY_ALREADY_REPORTED로 응답한다. 작성자가 자기 기록을 신고해도 막지 않는다. "
                    + "검토 전 신고가 5건 이상 쌓이면 서버가 같은 요청 안에서 기록을 PENDING_REVIEW로 바꿔, 작성자가 아닌 사람에게서 감춘다. "
                    + "기록이 없거나 비공개이거나 ACTIVE가 아니면(검토 대기, 숨김) 404 NOT_FOUND로 응답한다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @PostMapping("/api/memories/{memoryId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    void report(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long memoryId,
            @Valid @RequestBody MemoryReportRequest request) {
        memoryReportService.report(loginMember.memberId(), memoryId, request.toCommand());
    }
}
