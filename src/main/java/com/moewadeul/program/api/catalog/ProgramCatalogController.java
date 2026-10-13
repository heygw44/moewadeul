package com.moewadeul.program.api.catalog;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.program.application.catalog.ProgramQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ProgramCatalogController {

    private final ProgramQueryService programQueryService;

    @Operation(
            summary = "공식 행사 목록",
            description = "취소되지 않은 행사를 행사 시작 시각이 이른 순서로 돌려준다(시작 시각이 같으면 programId 순서). 로그인하지 않아도 조회할 수 있다. "
                    + "status를 보내면 UPCOMING(신청 시작 전), OPEN(신청 시작 시각 이상, 마감 시각 미만), CLOSED(마감 시각 이후) 중 그 단계의 행사만 준다. "
                    + "항목마다 programId, title, locationText, spotId(지도 장소와 연결하지 않았으면 null), startAt, endAt, applyOpenAt, applyCloseAt, "
                    + "capacity, remainingSeats(정원에서 결제 대기·확정 신청 수를 뺀 값, 0 미만이면 0), fee, overnight, status가 있다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/api/programs")
    ProgramPageResponse list(@Valid @ParameterObject @ModelAttribute ProgramListRequest request) {
        return ProgramPageResponse.from(programQueryService.list(request.toQuery()));
    }

    @Operation(
            summary = "공식 행사 상세",
            description = "행사 하나의 정보를 돌려준다. 로그인하지 않아도 조회할 수 있다. 목록 항목에 description과 paymentDeadlineMinutes가 더해진다. "
                    + "로그인한 회원에게 이 행사의 신청이 있으면 가장 최근 신청을 myApplication(applicationId, status, paymentDueAt)으로 담고, "
                    + "비로그인이거나 신청이 없으면 이 필드를 뺀다. "
                    + "취소된 행사도 status가 CANCELED인 상세로 돌려준다. 행사가 없으면 404 NOT_FOUND이다.")
    @GetMapping("/api/programs/{programId}")
    ProgramDetailResponse detail(@PathVariable long programId, @AuthenticationPrincipal LoginMember loginMember) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return ProgramDetailResponse.from(programQueryService.detail(programId, viewerId));
    }

    @Operation(
            summary = "내 행사 신청 목록",
            description =
                    "로그인한 회원 본인의 행사 신청을 최근 신청부터(applicationId 내림차순) 돌려준다. 이메일 인증 전의 회원도 조회할 수 있다. "
                            + "status를 생략하면 모든 상태를 주고, PENDING_PAYMENT, CONFIRMED, CANCELED, EXPIRED 중 하나를 보내면 그 상태만 준다. "
                            + "취소된 행사의 신청도 내 기록이라서 포함하고, 이때 program.status가 CANCELED이다. "
                            + "항목마다 applicationId, status, paymentDueAt, confirmedAt, canceledAt, cancelReason, createdAt, "
                            + "program(programId, title, locationText, startAt, endAt, fee, status)이 있다. "
                            + "program.fee는 지금의 참가비이고 결제한 금액이 아니다. program.status는 UPCOMING, OPEN, CLOSED, CANCELED 중 하나다. "
                            + "confirmedAt, canceledAt, cancelReason은 해당 상태가 아니면 null이다. "
                            + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                            + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/api/me/program-applications")
    MyProgramApplicationPageResponse listMine(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @ParameterObject @ModelAttribute MyProgramApplicationListRequest request) {
        return MyProgramApplicationPageResponse.from(
                programQueryService.listMine(loginMember.memberId(), request.toQuery()));
    }
}
