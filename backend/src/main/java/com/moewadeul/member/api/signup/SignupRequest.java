package com.moewadeul.member.api.signup;

import com.moewadeul.member.api.ConfirmedPassword;
import com.moewadeul.member.api.MailableEmail;
import com.moewadeul.member.api.PasswordsMatch;
import com.moewadeul.member.application.signup.SignupCommand;
import com.moewadeul.member.domain.Member;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@PasswordsMatch
public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 맞지 않아요.")
        @MailableEmail
        @Size(max = Member.EMAIL_MAX_LENGTH, message = "이메일은 " + Member.EMAIL_MAX_LENGTH + "자 이하여야 해요.")
        String email,

        @NotNull(message = "비밀번호를 입력해 주세요.") String password,

        @NotNull(message = "비밀번호 확인을 입력해 주세요.") String passwordConfirm,

        @NotBlank(message = "닉네임을 입력해 주세요.") @ValidNickname String nickname,

        @NotNull(message = AGE_CONFIRM_MESSAGE) @AssertTrue(message = AGE_CONFIRM_MESSAGE)
        Boolean ageConfirmed)
        implements ConfirmedPassword {

    private static final String AGE_CONFIRM_MESSAGE = "만 14세 이상만 가입할 수 있어요.";

    public SignupCommand toCommand(String requestIp) {
        return new SignupCommand(email, password, nickname, requestIp);
    }

    @Override
    public String passwordToConfirm() {
        return password;
    }

    @Override
    public String passwordConfirmation() {
        return passwordConfirm;
    }

    @Override
    public String confirmationFieldName() {
        return "passwordConfirm";
    }

    @Override
    public String toString() {
        return "SignupRequest[nickname=" + nickname + "]";
    }
}
