package com.moewadeul.member.api.password;

import com.moewadeul.member.api.MailableEmail;
import com.moewadeul.member.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 맞지 않아요.")
        @MailableEmail
        @Size(max = Member.EMAIL_MAX_LENGTH, message = "이메일은 " + Member.EMAIL_MAX_LENGTH + "자 이하여야 해요.")
        String email) {

    // 레코드 기본 toString은 구성요소를 모두 찍는다. 이메일이 로그에 새지 않도록 재정의했다.
    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
