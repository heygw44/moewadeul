package com.moewadeul.trust.api.profile;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.member.application.profile.MyInfo;
import com.moewadeul.member.application.profile.MyInfoService;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final MyInfoService myInfoService;
    private final TrustSummaryService trustSummaryService;

    private final WithdrawalService withdrawalService;
    private final LoginSessionManager loginSessionManager;

    MeController(
            MyInfoService myInfoService,
            TrustSummaryService trustSummaryService,
            WithdrawalService withdrawalService,
            LoginSessionManager loginSessionManager) {
        this.myInfoService = myInfoService;
        this.trustSummaryService = trustSummaryService;
        this.withdrawalService = withdrawalService;
        this.loginSessionManager = loginSessionManager;
    }

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 회원의 계정 정보, 자기 신고 연령대·성별, 본인확인 여부와 신뢰 단계를 돌려준다. " + "이메일 인증 전인 회원도 부를 수 있다.")
    @GetMapping
    MeResponse find(@AuthenticationPrincipal LoginMember loginMember) {
        long memberId = loginMember.memberId();
        return toResponse(myInfoService.find(memberId));
    }

    @Operation(
            summary = "내 정보 수정",
            description = "닉네임과 자기 신고 연령대·성별을 바꾼다. 요청에 없는 필드는 그대로 두고, null로 보낸 연령대·성별은 지운다. "
                    + "닉네임은 null로 지울 수 없다. 다른 회원이 쓰는 닉네임이면 409로 응답하고, 응답 형태는 내 정보 조회와 같다.")
    @PatchMapping
    MeResponse update(@AuthenticationPrincipal LoginMember loginMember, @RequestBody MeUpdateRequest request) {
        long memberId = loginMember.memberId();
        return toResponse(myInfoService.update(memberId, request.toCommand()));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "비밀번호를 다시 확인한 뒤 개인정보를 지우고 모든 세션을 삭제한다. 이메일 인증 전인 회원도 탈퇴할 수 있다. "
                    + "비밀번호가 틀리면 401(LOGIN_FAILED)이고 로그인 실패로 기록되어 로그인 잠금 계산에 들어간다. "
                    + "실패가 쌓여 잠겨 있으면 비밀번호를 확인하지 않고 429 LOGIN_LOCKED와 Retry-After 헤더를 돌려준다. "
                    + "진행 중인 베이스캠프와 결제 대기 행사 신청은 응답 뒤에 이벤트 처리기가 정리한다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody WithdrawRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        withdrawalService.withdraw(loginMember.memberId(), request.password(), httpRequest.getRemoteAddr());
        loginSessionManager.logout(httpRequest, httpResponse);
    }

    private MeResponse toResponse(MyInfo myInfo) {
        return MeResponse.of(myInfo, trustSummaryService.summarize(myInfo.memberId()));
    }
}
