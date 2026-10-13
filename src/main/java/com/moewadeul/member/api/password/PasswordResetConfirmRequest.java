package com.moewadeul.member.api.password;

import com.moewadeul.member.api.ConfirmedPassword;
import com.moewadeul.member.api.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 비밀번호 규칙(길이와 문자 종류)은 서비스가 검사한다. 여기서는 너무 긴 입력을 싸게 거르는 상한만 둔다.
@PasswordsMatch(message = "새 비밀번호 확인이 새 비밀번호와 달라요.")
public record PasswordResetConfirmRequest(
        @NotBlank(message = "재설정 토큰을 입력해 주세요.") @Size(max = 128, message = "재설정 토큰이 맞지 않아요.")
        String token,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.") @Size(max = 128, message = "새 비밀번호가 너무 길어요.")
        String newPassword,

        @NotNull(message = "새 비밀번호 확인을 입력해 주세요.") @Size(max = 128, message = "새 비밀번호 확인이 너무 길어요.")
        String newPasswordConfirm)
        implements ConfirmedPassword {

    @Override
    public String passwordToConfirm() {
        return newPassword;
    }

    @Override
    public String passwordConfirmation() {
        return newPasswordConfirm;
    }

    @Override
    public String confirmationFieldName() {
        return "newPasswordConfirm";
    }

    // 레코드 기본 toString은 구성요소를 모두 찍는다. 토큰과 비밀번호가 로그에 새지 않도록 재정의했다.
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[***]";
    }
}
