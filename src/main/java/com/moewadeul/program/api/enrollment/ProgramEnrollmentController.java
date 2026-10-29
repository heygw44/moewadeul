package com.moewadeul.program.api.enrollment;

import com.moewadeul.common.idempotency.IdempotencyExecutor;
import com.moewadeul.common.idempotency.IdempotentRequest;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.program.application.enrollment.ProgramApplyService;
import com.moewadeul.program.application.enrollment.ProgramVacancyAlertService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ProgramEnrollmentController {

    private final ProgramApplyService programApplyService;
    private final ProgramVacancyAlertService programVacancyAlertService;
    private final IdempotencyExecutor idempotencyExecutor;

    @Operation(
            summary = "행사 선착순 신청",
            description = "로그인한 인증 회원이 행사에 신청한다. Idempotency-Key 헤더가 필요하다. 없으면 400 IDEMPOTENCY_KEY_REQUIRED, "
                    + "영문·숫자·'-'·'_' 1~64자가 아니면 400 INVALID_INPUT이다. 같은 키로 다시 보내면 처음 결과를 그대로 돌려주고, "
                    + "처음 요청이 아직 처리 중이면 409 IDEMPOTENCY_IN_PROGRESS이다. "
                    + "성공하면 201과 applicationId, status(PENDING_PAYMENT), paymentDueAt(결제 기한), amount(결제할 금액)를 돌려준다. "
                    + "오류는 이 순서로 처음 걸린 하나만 응답한다. 행사가 없으면 404 NOT_FOUND, 취소된 행사이면 409 PROGRAM_INVALID_STATE, "
                    + "신청 시작 전이거나 마감 뒤이면 400 PROGRAM_NOT_IN_APPLY_PERIOD, 숙박 행사인데 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, "
                    + "이미 결제 대기·확정 신청이 있으면 409 PROGRAM_ALREADY_APPLIED, 남은 자리가 없으면 409 PROGRAM_SOLD_OUT이다. "
                    + "취소하거나 만료된 신청이 있는 회원은 다시 신청할 수 있다.")
    @PostMapping("/api/programs/{programId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    ProgramApplyResponse apply(
            @PathVariable long programId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal LoginMember loginMember) {
        long memberId = loginMember.memberId();
        return idempotencyExecutor.execute(
                new IdempotentRequest(
                        memberId, idempotencyKey, "POST /api/programs/" + programId + "/applications", null),
                HttpStatus.CREATED,
                ProgramApplyResponse.class,
                () -> ProgramApplyResponse.from(programApplyService.apply(memberId, programId)));
    }

    @Operation(
            summary = "행사 빈자리 알림 신청",
            description = "로그인한 인증 회원이 행사의 빈자리 알림을 신청한다. 새로 신청하면 201, 이미 신청한 상태이면 200이고 본문은 없다. "
                    + "자리가 모두 찬 행사에만 신청할 수 있다. 신청은 회원이 해제할 때까지 남고, 자리가 돌아올 때마다 알림을 보낸다. "
                    + "오류는 이 순서로 처음 걸린 하나만 응답한다. 행사가 없으면 404 NOT_FOUND, "
                    + "취소된 행사이거나 신청 마감 시각 이후이면 409 PROGRAM_INVALID_STATE, "
                    + "남은 자리가 있으면 409 PROGRAM_SEATS_AVAILABLE이다.")
    @PostMapping("/api/programs/{programId}/vacancy-alerts")
    ResponseEntity<Void> subscribeVacancyAlert(
            @PathVariable long programId, @AuthenticationPrincipal LoginMember loginMember) {
        boolean created = programVacancyAlertService.subscribe(loginMember.memberId(), programId);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
                .build();
    }

    @Operation(
            summary = "행사 빈자리 알림 해제",
            description =
                    "로그인한 인증 회원이 행사의 빈자리 알림 신청을 해제한다. 신청한 적이 없어도, 행사가 취소·마감됐어도 204이다. " + "행사가 없으면 404 NOT_FOUND이다.")
    @DeleteMapping("/api/programs/{programId}/vacancy-alerts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribeVacancyAlert(@PathVariable long programId, @AuthenticationPrincipal LoginMember loginMember) {
        programVacancyAlertService.unsubscribe(loginMember.memberId(), programId);
    }
}
