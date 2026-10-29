package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplicationCancelService;
import com.moewadeul.basecamp.application.membership.BasecampApplicationListService;
import com.moewadeul.basecamp.application.membership.BasecampApplyService;
import com.moewadeul.basecamp.application.membership.BasecampApprovalService;
import com.moewadeul.basecamp.application.membership.BasecampMembershipService;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BasecampMembershipController {

    private final BasecampApplyService basecampApplyService;
    private final BasecampApplicationCancelService basecampApplicationCancelService;
    private final BasecampApplicationListService basecampApplicationListService;
    private final BasecampApprovalService basecampApprovalService;
    private final BasecampMembershipService basecampMembershipService;

    @Operation(
            summary = "베이스캠프 합류 신청",
            description = "본인확인을 마친 회원(신뢰 단계 1 이상)이 모집 중인 베이스캠프에 합류를 신청하고, 201과 함께 applicationId와 status(PENDING)를 준다. "
                    + "요청 본문과 message는 생략할 수 있고, message는 500자 이하여야 한다(넘으면 400 INVALID_INPUT). "
                    + "스스로 취소했던 신청은 같은 applicationId로 다시 PENDING이 된다. 캠프 리더에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 신뢰 단계가 1 미만이면 403 TRUST_LEVEL_INSUFFICIENT, "
                    + "모집 중이 아니면 409 BASECAMP_INVALID_STATE, 이미 대기 중이거나 멤버(캠프 리더 포함)이면 409 BASECAMP_ALREADY_APPLIED, "
                    + "거절·탈퇴·강퇴된 적이 있으면 409 BASECAMP_REAPPLY_NOT_ALLOWED, "
                    + "합류 조건(최소 신뢰 단계 2, 본인확인한 연령대, 본인확인한 성별)을 충족하지 못하면 403 BASECAMP_CONDITION_NOT_MET, "
                    + "같은 기간에 진행 중인(모집 중·마감·확정) 다른 베이스캠프의 ACTIVE 멤버이면 409 BASECAMP_DATE_CONFLICT, "
                    + "대기 신청이 이미 20건이면 409 BASECAMP_PENDING_LIMIT이다.")
    @PostMapping("/api/basecamps/{basecampId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    BasecampApplyResponse apply(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @RequestBody(required = false) BasecampApplyRequest request) {
        BasecampApplyRequest body = request == null ? BasecampApplyRequest.empty() : request;
        return BasecampApplyResponse.from(
                basecampApplyService.apply(body.toCommand(basecampId, loginMember.memberId())));
    }

    @Operation(
            summary = "베이스캠프 합류 신청 취소",
            description = "회원이 대기 중인 자기 합류 신청을 취소하고 204로 응답한다. "
                    + "모집 중이거나 마감된 베이스캠프에서만 할 수 있다. 취소한 신청은 같은 베이스캠프에 다시 낼 수 있다. "
                    + "베이스캠프가 없거나 내 신청이 없으면 404 NOT_FOUND, "
                    + "베이스캠프가 모집 중이거나 마감된 상태가 아니거나 신청이 대기 중이 아니면 409 BASECAMP_INVALID_STATE로 응답한다.")
    @DeleteMapping("/api/basecamps/{basecampId}/applications/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelApplication(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        basecampApplicationCancelService.cancel(basecampId, loginMember.memberId());
    }

    @Operation(
            summary = "베이스캠프 합류 신청 목록",
            description =
                    "캠프 리더가 자기 베이스캠프에 들어온 합류 신청을 신청이 오래된 순서로 돌려준다(신청 시각이 같으면 신청 ID 순서). "
                            + "신청 시각(appliedAt)은 가장 최근에 신청한 시각이라서, 취소했다가 다시 신청한 사람은 다시 신청한 시각으로 정렬되어 뒤로 간다. "
                            + "status를 생략하면 결정을 기다리는 PENDING 신청만 주고, PENDING, APPROVED, REJECTED, CANCELED, EXPIRED 중 하나를 보내면 그 상태만 준다. "
                            + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                            + "항목마다 applicationId, status, message, appliedAt과 신청자 프로필(memberId, nickname, ageGroup, ageGroupVerified, gender, "
                            + "genderVerified, trustLevel, completedCompanions)이 있다. 이메일과 출생연도는 응답하지 않는다. "
                            + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/api/basecamps/{basecampId}/applications")
    PageResponse<BasecampApplicationResponse> listApplications(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @Valid @ParameterObject @ModelAttribute BasecampApplicationListRequest request) {
        return PageResponse.from(
                basecampApplicationListService.list(basecampId, loginMember.memberId(), request.toQuery()),
                BasecampApplicationResponse::from);
    }

    @Operation(
            summary = "베이스캠프 합류 신청 승인",
            description = "캠프 리더가 대기 중인 합류 신청을 승인하고, 200과 함께 승인한 뒤의 인원(headcount)과 베이스캠프 상태(status)를 준다. "
                    + "신청자는 멤버가 되고, 승인해서 정원이 차면 베이스캠프는 자동으로 마감되어 status가 CLOSED다. 신청자에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답하며, 거부되면 신청은 대기 중으로 남는다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 이 베이스캠프의 신청이 아니면 404 NOT_FOUND, "
                    + "모집 중이 아니거나 신청이 대기 중이 아니면 409 BASECAMP_INVALID_STATE, 정원이 이미 차 있으면 409 BASECAMP_FULL, "
                    + "신청자가 지금 합류 조건(최소 신뢰 단계, 본인확인한 연령대, 본인확인한 성별)을 충족하지 못하면 403 BASECAMP_CONDITION_NOT_MET, "
                    + "신청자가 같은 기간에 진행 중인(모집 중·마감·확정) 다른 베이스캠프의 ACTIVE 멤버이면 409 BASECAMP_DATE_CONFLICT이다.")
    @PostMapping("/api/basecamps/{basecampId}/applications/{applicationId}/approve")
    BasecampApproveResponse approveApplication(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @PathVariable long applicationId) {
        return BasecampApproveResponse.from(
                basecampApprovalService.approve(basecampId, applicationId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 합류 신청 거절",
            description = "캠프 리더가 대기 중인 합류 신청을 거절하고, 200과 함께 applicationId와 status(REJECTED)를 준다. "
                    + "거절된 회원은 같은 베이스캠프에 다시 신청할 수 없다(409 BASECAMP_REAPPLY_NOT_ALLOWED). 신청자에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, 이 베이스캠프의 신청이 아니면 404 NOT_FOUND, "
                    + "모집 중이 아니거나 신청이 대기 중이 아니면 409 BASECAMP_INVALID_STATE로 응답한다.")
    @PostMapping("/api/basecamps/{basecampId}/applications/{applicationId}/reject")
    BasecampRejectResponse rejectApplication(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @PathVariable long applicationId) {
        return BasecampRejectResponse.from(
                basecampApprovalService.reject(basecampId, applicationId, loginMember.memberId()));
    }

    @Operation(
            summary = "베이스캠프 탈퇴",
            description = "멤버가 베이스캠프에서 탈퇴하고 204로 응답한다. 탈퇴한 회원은 같은 베이스캠프에 다시 신청할 수 없다. "
                    + "확정된 뒤 출발 48시간 안에 탈퇴하면 임박 탈퇴로 기록된다. 정원이 차서 자동 마감된 베이스캠프는 빈자리가 생기면 다시 모집 중이 되고, "
                    + "캠프 리더가 직접 마감한 베이스캠프는 마감 그대로다. "
                    + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND, "
                    + "베이스캠프가 완료되었거나 취소되었으면 409 BASECAMP_INVALID_STATE, ACTIVE 멤버가 아니면 404 NOT_FOUND, "
                    + "캠프 리더이면 409 BASECAMP_LEADER_CANNOT_LEAVE이다.")
    @DeleteMapping("/api/basecamps/{basecampId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        basecampMembershipService.leave(basecampId, loginMember.memberId());
    }

    @Operation(
            summary = "베이스캠프 멤버 강퇴",
            description =
                    "캠프 리더가 멤버를 강퇴하고 204로 응답한다. 본문의 reason은 NO_CONTACT, CONDITION_MISMATCH, INAPPROPRIATE_BEHAVIOR, OTHER 중 하나여야 하고 "
                            + "없거나 다른 값이면 400 INVALID_INPUT이다. 확정되기 전(모집 중, 마감)에만 할 수 있다. "
                            + "강퇴된 회원은 같은 베이스캠프에 다시 신청할 수 없고, 강퇴된 회원에게 알릴 이벤트가 같은 트랜잭션에서 기록된다. "
                            + "정원이 차서 자동 마감된 베이스캠프는 빈자리가 생기면 다시 모집 중이 된다. "
                            + "다음 순서로 검사하고 처음 걸린 이유로 응답한다. "
                            + "베이스캠프가 없으면 404 NOT_FOUND, 캠프 리더가 아니면 403 ACCESS_DENIED, "
                            + "베이스캠프가 확정되었거나 그 뒤 상태이면 409 BASECAMP_INVALID_STATE, 대상이 ACTIVE 멤버가 아니면 404 NOT_FOUND, "
                            + "대상이 캠프 리더이면 409 BASECAMP_LEADER_CANNOT_LEAVE이다.")
    @PostMapping("/api/basecamps/{basecampId}/members/{memberId}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void kick(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long basecampId,
            @PathVariable long memberId,
            @Valid @RequestBody BasecampKickRequest request) {
        basecampMembershipService.kick(basecampId, memberId, loginMember.memberId(), request.reason());
    }
}
