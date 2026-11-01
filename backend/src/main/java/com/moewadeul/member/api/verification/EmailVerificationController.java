package com.moewadeul.member.api.verification;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.application.verification.VerificationResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/email-verification")
class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final LoginSessionManager loginSessionManager;

    EmailVerificationController(
            EmailVerificationService emailVerificationService, LoginSessionManager loginSessionManager) {
        this.emailVerificationService = emailVerificationService;
        this.loginSessionManager = loginSessionManager;
    }

    @Operation(
            summary = "이메일 인증 코드 확인",
            description = "메일로 받은 6자리 코드를 확인한다. 성공하면 회원이 ACTIVE가 되고 현재 세션이 이메일 인증이 필요한 API를 바로 쓸 수 있다. "
                    + "코드를 5번 틀리면 그 코드는 무효가 되어 재발송으로 새 코드를 받아야 한다.")
    @PostMapping
    EmailVerificationResponse verify(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        VerificationResult result = emailVerificationService.verify(loginMember.memberId(), request.code());
        loginSessionManager.markEmailVerified(httpRequest, httpResponse);
        return EmailVerificationResponse.from(result);
    }

    @Operation(
            summary = "이메일 인증 코드 재발송",
            description = "새 인증 코드를 메일로 보낸다. 마지막 발송 뒤 60초 안에 다시 요청하거나 하루 5회, IP당 시간당 20회를 넘으면 "
                    + "429와 Retry-After로 응답한다. 가장 최근에 발송한 코드만 유효하다.")
    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resend(@AuthenticationPrincipal LoginMember loginMember, HttpServletRequest httpRequest) {
        emailVerificationService.requestResend(loginMember.memberId(), httpRequest.getRemoteAddr());
    }
}
