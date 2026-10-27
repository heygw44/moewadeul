package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.domain.KickReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;

public record BasecampKickRequest(
        @Schema(implementation = KickReason.class, description = "강퇴 사유.") @NotNull(message = "강퇴 사유를 입력해야 합니다.")
        String reason) {

    @AssertTrue(message = "강퇴 사유는 NO_CONTACT, CONDITION_MISMATCH, INAPPROPRIATE_BEHAVIOR, OTHER 중 하나여야 해요.")
    public boolean isReasonValid() {
        return reason == null
                || Arrays.stream(KickReason.values())
                        .anyMatch(candidate -> candidate.name().equals(reason));
    }

    KickReason toReason() {
        return KickReason.valueOf(reason);
    }
}
