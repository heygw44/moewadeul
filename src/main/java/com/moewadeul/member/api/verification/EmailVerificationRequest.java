package com.moewadeul.member.api.verification;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequest(
        @NotBlank(message = "인증 코드를 입력해 주세요.") String code) {

    @Override
    public String toString() {
        return "EmailVerificationRequest[***]";
    }
}
