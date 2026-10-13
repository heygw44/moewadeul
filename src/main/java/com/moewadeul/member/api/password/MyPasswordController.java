package com.moewadeul.member.api.password;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.member.application.password.PasswordChangeCommand;
import com.moewadeul.member.application.password.PasswordChangeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/password")
class MyPasswordController {

    private final PasswordChangeService passwordChangeService;
    private final LoginSessionManager loginSessionManager;

    MyPasswordController(PasswordChangeService passwordChangeService, LoginSessionManager loginSessionManager) {
        this.passwordChangeService = passwordChangeService;
        this.loginSessionManager = loginSessionManager;
    }

    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다. 이메일 인증 전인 회원도 부를 수 있다. "
                    + "성공하면 그 회원의 다른 세션은 모두 삭제되고, 요청한 세션은 새 세션 ID로 로그인을 유지한다. "
                    + "현재 비밀번호가 틀리면 400 MEMBER_CURRENT_PASSWORD_MISMATCH이고 로그인 실패 횟수에 들어간다. "
                    + "실패가 쌓여 잠겨 있으면 429 LOGIN_LOCKED와 Retry-After 헤더를 돌려준다. "
                    + "새 비밀번호가 규칙을 어기면 400 MEMBER_PASSWORD_POLICY, 현재 비밀번호와 같으면 400 MEMBER_PASSWORD_UNCHANGED, "
                    + "newPasswordConfirm이 newPassword와 다르면 400 INVALID_INPUT이다.")
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest httpRequest) {
        // 로그인한 요청이라 세션이 반드시 있다. 서비스는 이 세션만 남기고 같은 회원의 다른 세션을 지운다.
        String currentSessionId = httpRequest.getSession(false).getId();
        passwordChangeService.change(new PasswordChangeCommand(
                loginMember.memberId(),
                request.currentPassword(),
                request.newPassword(),
                httpRequest.getRemoteAddr(),
                currentSessionId));
        loginSessionManager.changeSessionId(httpRequest);
    }
}
