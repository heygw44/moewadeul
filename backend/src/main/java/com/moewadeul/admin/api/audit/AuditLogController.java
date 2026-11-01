package com.moewadeul.admin.api.audit;

import com.moewadeul.admin.application.audit.AuditLogQueryService;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @Operation(
            summary = "관리자 감사 로그 조회",
            description =
                    "관리자 조치 기록을 최근 것부터 돌려준다. adminId, targetType(MEMBER_REPORT, SANCTION, SPOT, COMMUNITY_POST, COMMUNITY_COMMENT, MEMORY, PROGRAM, SYNC_JOB_RUN), "
                            + "from(이상), to(미만)은 선택이고 시각은 ISO-8601이다. page는 0부터 세고, size는 기본 20, 1~50이다. "
                            + "detail에는 신고 내용, 메모, 제재 사유 원문이 들어 있지 않다.")
    @GetMapping
    PageResponse<AuditLogResponse> findLogs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageSlice.DEFAULT_SIZE) @Min(1) @Max(PageSlice.MAX_SIZE) int size) {
        return PageResponse.from(
                auditLogQueryService.findLogs(adminId, targetType, from, to, page, size), AuditLogResponse::from);
    }
}
