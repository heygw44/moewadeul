package com.moewadeul.admin.api.trust;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.trust.application.sanction.SanctionLiftService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sanctions")
class AdminSanctionController {

    private final SanctionLiftService sanctionLiftService;

    AdminSanctionController(SanctionLiftService sanctionLiftService) {
        this.sanctionLiftService = sanctionLiftService;
    }

    @Operation(
            summary = "제재 해제",
            description =
                    "적용 중(ACTIVE)인 제재를 해제한다. 경고와 확정 정지 모두 해제할 수 있고, 정지를 해제하면 회원의 정지 상태를 "
                            + "남은 정지에 맞춰 다시 정한다. 사유(reason)는 필수이다. 조기 종료(END_EARLY)는 제재가 ENDED_EARLY가 되어 이력에 남고, "
                            + "다음 제재 단계와 신뢰 회원 판정에 계속 반영된다. 처분 취소(REVOKE)는 제재가 REVOKED가 되어 이력에서 빠진다. "
                            + "이미 정리한 베이스캠프와 결제 대기 신청은 되돌리지 않는다. "
                            + "사유가 없거나 올바르지 않으면 400 INVALID_INPUT, 제재가 없으면 404 NOT_FOUND, 적용 중이 아니면 409 SANCTION_INVALID_STATE이다.")
    @PostMapping("/{id}/lift")
    SanctionLiftResponse lift(
            @PathVariable long id,
            @AuthenticationPrincipal LoginMember admin,
            @Valid @RequestBody SanctionLiftRequest request) {
        return SanctionLiftResponse.from(sanctionLiftService.lift(id, request.reason(), admin.memberId()));
    }
}
