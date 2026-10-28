package com.moewadeul.trust.api.identity;

import com.moewadeul.trust.application.identity.IdentityVerifyCommand;
import com.moewadeul.trust.domain.identity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IdentityVerificationRequest(
        @Schema(description = "출생연도. 1900년부터 올해(한국 시각)까지.", example = "1995") @NotNull(message = "출생연도를 입력해야 합니다.")
        Integer birthYear,

        @Schema(description = "성별. MALE 또는 FEMALE.") @NotNull(message = "성별을 입력해야 합니다.")
        Gender gender,

        @Schema(description = "시연용 식별 문자열. 같은 값이면 같은 사람으로 본다.", example = "demo-person-1")
        @NotBlank(message = "시연용 식별 문자열을 입력해 주세요.")
        @Size(max = MAX_DEMO_IDENTITY_KEY_LENGTH, message = "시연용 식별 문자열은 100자 이하여야 해요.")
        String demoIdentityKey) {

    static final int MAX_DEMO_IDENTITY_KEY_LENGTH = 100;

    IdentityVerifyCommand toCommand() {
        return new IdentityVerifyCommand(birthYear, gender, demoIdentityKey);
    }

    @Override
    public String toString() {
        return "IdentityVerificationRequest[gender=" + gender + ", birthYear=****, demoIdentityKey=****]";
    }
}
