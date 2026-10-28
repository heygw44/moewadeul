package com.moewadeul.member.api.password;

import com.moewadeul.member.api.ConfirmedPassword;
import com.moewadeul.member.api.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@PasswordsMatch(message = "새 비밀번호 확인이 새 비밀번호와 달라요.")
public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.") @Size(max = 128, message = "현재 비밀번호가 너무 길어요.")
        String currentPassword,

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

    @Override
    public String toString() {
        return "PasswordChangeRequest[***]";
    }
}
