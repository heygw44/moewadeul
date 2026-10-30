package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampEditService;
import com.moewadeul.basecamp.application.lifecycle.BasecampOpenService;
import com.moewadeul.basecamp.application.lifecycle.BasecampTransitionService;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.idempotency.application.IdempotencyExecutor;
import com.moewadeul.idempotency.application.IdempotentRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BasecampLifecycleController {

    private final BasecampOpenService basecampOpenService;
    private final BasecampTransitionService basecampTransitionService;
    private final BasecampEditService basecampEditService;
    private final IdempotencyExecutor idempotencyExecutor;

    @Operation(
            summary = "베이스캠프 열기",
            description = "본인확인을 마친 회원(신뢰 단계 1 이상)이 장소에 베이스캠프를 열고, 201과 함께 basecampId와 status(RECRUITING)를 준다. "
                    + "Idempotency-Key 헤더가 필요하다. 없으면 400 IDEMPOTENCY_KEY_REQUIRED, 영문·숫자·'-'·'_' 1~64자가 아니면 400 INVALID_INPUT이다. "
                    + "같은 키로 다시 보내면 베이스캠프를 새로 열지 않고 처음 결과를 그대로 돌려주고, 처음 요청이 아직 처리 중이면 409 IDEMPOTENCY_IN_PROGRESS이다. "
                    + "캠프 리더는 첫 멤버가 된다. 출발일은 내일부터 60일 뒤까지, 박 수는 1~3박, 정원은 캠프 리더를 포함해 2~6명이다. "
                    + "일정이 어긋나면 400 BASECAMP_SCHEDULE_INVALID, 정원이 어긋나면 400 BASECAMP_CAPACITY_INVALID로 응답한다. "
                    + "합류 조건(joinCondition)은 생략할 수 있다. 연령대는 하한·상한을 함께 보내야 하고 20~60의 십 단위이며, "
                    + "sameGenderOnly가 true이면 캠프 리더의 본인확인 성별과 같은 사람만 받는다. 값이 어긋나면 400 INVALID_INPUT이다. "
                    + "필수 값이 없거나 제목이 100자, 설명이 2,000자를 넘어도 400 INVALID_INPUT이다. "
                    + "신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT로 응답한다. "
                    + "장소가 없거나 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND로 응답한다. "
                    + "공원 경계 경고가 붙은 박지이면 400 BASECAMP_WARNING_SPOT으로 응답한다. "
                    + "모집 중이거나 마감된 베이스캠프를 이미 3개 열었으면 400 BASECAMP_OPEN_LIMIT로 응답한다. "
                    + "같은 기간에 진행 중인(모집 중·마감·확정) 다른 베이스캠프의 ACTIVE 멤버이면 409 BASECAMP_DATE_CONFLICT로 응답한다.")
    @PostMapping("/api/basecamps")
    @ResponseStatus(HttpStatus.CREATED)
    BasecampOpenResponse open(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BasecampOpenRequest request) {
        long memberId = loginMember.memberId();
        return idempotencyExecutor.execute(
                new IdempotentRequest(memberId, idempotencyKey, "POST /api/basecamps", request),
                HttpStatus.CREATED,
                BasecampOpenResponse.class,
                () -> BasecampOpenResponse.from(basecampOpenService.open(memberId, request.toCommand())));
    }

    @Operation(
            summary = "베이스캠프 마감",
            description = "캠프 리더가 모집 중인 베이스캠프를 직접 마감하고, 200과 함께 basecampId와 status(CLOSED)를 준다. "
                    + "직접 마감한 베이스캠프는 빈자리가 생겨도 저절로 다시 열리지 않는다. 본문은 없다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 모집 중이 아니면 409 BASECAMP_INVALID_STATE이다.")
    @PostMapping("/api/basecamps/{basecampId}/close")
    BasecampStatusResponse close(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.close(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 모집 재개",
            description = "캠프 리더가 마감된 베이스캠프의 모집을 다시 열고, 200과 함께 basecampId와 status(RECRUITING)를 준다. 본문은 없다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 마감 상태가 아니면 409 BASECAMP_INVALID_STATE, "
                    + "정원이 가득 차 있으면 409 BASECAMP_FULL이다.")
    @PostMapping("/api/basecamps/{basecampId}/reopen")
    BasecampStatusResponse reopen(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.reopen(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 확정",
            description = "캠프 리더가 모집 중이거나 마감된 베이스캠프를 확정하고, 200과 함께 basecampId와 status(CONFIRMED)를 준다. "
                    + "남은 대기 신청은 EXPIRED가 되고, 캠프 리더를 포함한 ACTIVE 멤버 전원에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. 본문은 없다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 모집 중도 마감도 아니면 409 BASECAMP_INVALID_STATE, "
                    + "인원이 2명 미만이면 409 BASECAMP_NOT_ENOUGH_MEMBERS이다.")
    @PostMapping("/api/basecamps/{basecampId}/confirm")
    BasecampStatusResponse confirm(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.confirm(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 취소",
            description =
                    "캠프 리더가 베이스캠프를 취소하고, 200과 함께 basecampId와 status(CANCELED)를 준다. 확정된 뒤에도 출발일 전날(한국 시간)까지는 취소할 수 있다. "
                            + "취소 사유는 LEADER로 저장하고, 남은 대기 신청은 EXPIRED가 되며, 캠프 리더를 뺀 ACTIVE 멤버에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. 본문은 없다. "
                            + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                            + "이미 완료되었거나 취소되었거나 출발일 0시(한국 시간)가 지났으면 409 BASECAMP_INVALID_STATE이다.")
    @PostMapping("/api/basecamps/{basecampId}/cancel")
    BasecampStatusResponse cancel(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        return BasecampStatusResponse.from(basecampTransitionService.cancel(basecampId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 연락 수단 등록",
            description = "캠프 리더가 연락 수단을 등록하거나 바꾸고, 200과 함께 basecampId와 status를 준다. "
                    + "contactInfo는 필수이고 255자 이하의 https URL이어야 하며, 어긋나면 400 INVALID_INPUT이다. "
                    + "연락 수단은 베이스캠프가 확정된 뒤부터 완료 후 7일까지 ACTIVE 멤버에게만 상세 응답에 나타난다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "요청 값이 어긋나면 400 INVALID_INPUT, 베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                    + "완료되었거나 취소되었으면 409 BASECAMP_INVALID_STATE이다.")
    @PutMapping("/api/basecamps/{basecampId}/contact")
    BasecampStatusResponse registerContact(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody BasecampContactRequest request) {
        return BasecampStatusResponse.from(
                basecampEditService.registerContact(basecampId, loginMember.memberId(), request.contactInfo()));
    }

    @Operation(
            summary = "베이스캠프 수정",
            description =
                    "캠프 리더가 title, description, joinCondition, capacity 가운데 보낸 필드만 고치고, 200과 함께 basecampId와 status를 준다. "
                            + "한 필드도 보내지 않으면 400 INVALID_INPUT이다. 보내지 않은 필드는 그대로 둔다. "
                            + "joinCondition을 보내면 합류 조건 전체를 바꾸며 규칙은 열 때와 같다(sameGenderOnly는 캠프 리더의 본인확인 성별을 쓴다). "
                            + "정원은 캠프 리더를 포함해 2~6명이고 늘리기만 할 수 있다. 정원이 차서 자동 마감된 베이스캠프는 정원을 늘리면 다시 모집 중이 된다. "
                            + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "요청 값이 어긋나면 400 INVALID_INPUT, 베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                            + "정원이 2~6명이 아니면 400 BASECAMP_CAPACITY_INVALID, 합류 조건 값이 올바르지 않으면 400 INVALID_INPUT, "
                            + "확정된 뒤이면 409 BASECAMP_INVALID_STATE, 정원을 줄이면 400 BASECAMP_CAPACITY_INVALID이다.")
    @PatchMapping("/api/basecamps/{basecampId}")
    BasecampStatusResponse revise(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody BasecampReviseRequest request) {
        return BasecampStatusResponse.from(
                basecampEditService.revise(basecampId, loginMember.memberId(), request.toCommand()));
    }
}
