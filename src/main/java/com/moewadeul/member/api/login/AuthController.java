package com.moewadeul.member.api.login;

import com.moewadeul.common.security.AfterSessionCommitFilter;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.member.application.login.LoginResult;
import com.moewadeul.member.application.login.MemberLoginService;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberStatus;
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

    @Operation(summary = "로그인")
    @PostMapping("/login")
    LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = memberLoginService.login(request.toCommand(httpRequest.getRemoteAddr()));
        // 인증 코드를 확인하기 전(UNVERIFIED)에도 로그인할 수 있다. 다만 이메일 인증이 필요한 경로는 ACTIVE 회원만 통과한다.
        boolean emailVerified = result.status() == MemberStatus.ACTIVE;
        loginSessionManager.login(
                new LoginMember(result.memberId(), result.role().name(), emailVerified), httpRequest, httpResponse);
        discardSessionIfMemberChanged(result, httpRequest, httpResponse);
        revokeSessionAfterCommitIfMemberChanged(result, httpRequest);
        return LoginResponse.from(result);
    }

    // 비밀번호를 비교하는 사이에 재설정·정지·탈퇴가 끝났다면 그 일이 세션을 지울 때는 이 세션이 없었다. 그래서 세션을 만든 직후에
    // 회원을 다시 읽어 바뀐 것이 보이면 방금 만든 세션을 버리고 로그인을 거부한다. 응답을 그에 맞게 돌려주려는 확인이다.
    private void discardSessionIfMemberChanged(
            LoginResult result, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            memberLoginService.ensureStillLoginable(result);
        } catch (MemberException e) {
            loginSessionManager.logout(httpRequest, httpResponse);
            throw e;
        }
    }

    // 세션은 응답을 커밋할 때 저장소에 저장되므로, 위 확인은 세션이 저장소에 들어가기 전에 한다. 그 확인과 저장 사이에 재설정이 끝나면
    // 재설정이 지울 세션도 확인이 볼 바뀐 값도 없다. 그래서 세션을 저장한 뒤에 한 번 더 확인하도록 맡긴다.
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
