package com.moewadeul.member.api;

import com.moewadeul.member.application.LoginCommand;
import com.moewadeul.member.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 맞지 않아요.")
        @Size(max = Member.EMAIL_MAX_LENGTH, message = "이메일은 " + Member.EMAIL_MAX_LENGTH + "자 이하여야 해요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {

    public LoginCommand toCommand(String ip) {
        return new LoginCommand(email, password, ip);
    }

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 이메일·비밀번호가 로그에 새지 않도록 우리가 재정의했다.
    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
