package com.moewadeul.member.api.login;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.security.AfterSessionCommitFilter;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.member.application.login.LoginResult;
import com.moewadeul.member.application.login.MemberLoginService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final MemberLoginService memberLoginService;
    private final LoginSessionManager loginSessionManager;

    AuthController(MemberLoginService memberLoginService, LoginSessionManager loginSessionManager) {
        this.memberLoginService = memberLoginService;
        this.loginSessionManager = loginSessionManager;
    }

    @Operation(
            summary = "로그인",
            description = "세션 쿠키를 발급한다. 이메일 인증 전(UNVERIFIED) 회원도 로그인할 수 있고, 응답의 status로 구분한다. "
                    + "이메일이나 비밀번호가 맞지 않거나 탈퇴한 계정이면 401 LOGIN_FAILED이다. "
                    + "같은 계정에서 실패가 쌓였거나 같은 IP의 실패가 많으면 429 LOGIN_LOCKED와 Retry-After 헤더로 응답한다. "
                    + "정지된 계정이면 403 MEMBER_SUSPENDED이고 응답에 suspendedUntil(영구 정지이면 없음)을 담는다.")
    @PostMapping("/login")
    LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = memberLoginService.login(request.toCommand(httpRequest.getRemoteAddr()));
        loginSessionManager.login(
                new LoginMember(result.memberId(), result.role().name(), result.emailVerified()),
                result.passwordChangedAt(),
                httpRequest,
                httpResponse);
        discardSessionIfMemberChanged(result, httpRequest, httpResponse);
        revokeSessionAfterCommitIfMemberChanged(result, httpRequest);
        return LoginResponse.from(result);
    }

    private void discardSessionIfMemberChanged(
            LoginResult result, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            memberLoginService.ensureStillLoginable(result);
        } catch (BusinessException e) {
            loginSessionManager.logout(httpRequest, httpResponse);
            throw e;
        }
    }

    private void revokeSessionAfterCommitIfMemberChanged(LoginResult result, HttpServletRequest httpRequest) {
        String sessionId = httpRequest.getSession().getId();
        AfterSessionCommitFilter.register(
                httpRequest, () -> memberLoginService.revokeSessionIfMemberChanged(result, sessionId));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginSessionManager.logout(httpRequest, httpResponse);
    }
}
