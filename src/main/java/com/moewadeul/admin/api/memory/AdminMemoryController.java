package com.moewadeul.admin.api.memory;

import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.web.PageResponse;
import com.moewadeul.memory.application.moderation.AdminMemoryQueryService;
import com.moewadeul.memory.application.moderation.MemoryModerationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/memories")
class AdminMemoryController {

    private final AdminMemoryQueryService queryService;
    private final MemoryModerationService moderationService;

    AdminMemoryController(AdminMemoryQueryService queryService, MemoryModerationService moderationService) {
        this.queryService = queryService;
        this.moderationService = moderationService;
    }

    @Operation(
            summary = "추억 기록 검토 목록",
            description = "status(PENDING_REVIEW 기본, 또는 HIDDEN)인 공개 기록을 돌려준다. 비공개 기록은 관리자도 보지 못해서 목록에 넣지 않는다. "
                    + "검토 전 신고 중 금전 요구·사기(MONEY_SCAM)가 있는 기록이 먼저이고, 같은 묶음에서는 상태가 바뀐 시각이 이른 순서다. "
                    + "각 항목에는 작성자, 대략 좌표, 글 전체, 사진, 검토 전 신고의 수, 사유별 수, 최근 신고 5건의 내용을 담는다. 정확한 좌표는 주지 않는다. "
                    + "다른 status는 400 INVALID_INPUT이다. page는 0부터 세고, size는 기본 20, 1~50이다.")
    @GetMapping
    PageResponse<AdminMemoryResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageSlice.DEFAULT_SIZE) @Min(1) @Max(PageSlice.MAX_SIZE) int size) {
        return PageResponse.from(queryService.list(status, page, size), AdminMemoryResponse::from);
    }

    @Operation(
            summary = "추억 기록 숨김",
            description = "ACTIVE나 검토 대기(PENDING_REVIEW)인 공개 기록을 HIDDEN으로 바꾼다. 신고가 없는 기록도 바로 숨길 수 있다. "
                    + "기록이 없거나 비공개이면 404 NOT_FOUND, 이미 숨긴 기록이면 409 MEMORY_INVALID_STATE이다.")
    @PostMapping("/{memoryId}/hide")
    MemoryStatusResponse hide(@PathVariable long memoryId, @AuthenticationPrincipal LoginMember admin) {
        return MemoryStatusResponse.from(moderationService.hide(memoryId, admin.memberId()));
    }

    @Operation(
            summary = "추억 기록 복구",
            description = "검토 대기(PENDING_REVIEW)나 숨긴(HIDDEN) 기록을 ACTIVE로 바꾸고, 그 기록의 검토 전 신고를 모두 검토를 마친 것으로 표시한다. "
                    + "그래서 복구한 기록은 새 신고가 5건 쌓여야 다시 검토 대기가 된다. "
                    + "기록이 없으면 404 NOT_FOUND, 이미 ACTIVE인 기록이면 409 MEMORY_INVALID_STATE이다.")
    @PostMapping("/{memoryId}/restore")
    MemoryStatusResponse restore(@PathVariable long memoryId, @AuthenticationPrincipal LoginMember admin) {
        return MemoryStatusResponse.from(moderationService.restore(memoryId, admin.memberId()));
    }
}
