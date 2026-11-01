package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.domain.KickReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record BasecampKickRequest(
        @Schema(description = "강퇴 사유.") @NotNull(message = "내보내는 이유를 골라 주세요.")
        KickReason reason) {}
