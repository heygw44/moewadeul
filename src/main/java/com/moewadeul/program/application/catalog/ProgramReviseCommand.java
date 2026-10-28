package com.moewadeul.program.application.catalog;

import com.moewadeul.common.web.PatchField;
import java.time.Instant;
import java.util.Objects;

public record ProgramReviseCommand(
        String title,
        String description,
        PatchField<Long> spotId,
        String locationText,
        Instant startAt,
        Instant endAt,
        Integer capacity,
        Integer fee,
        Instant applyOpenAt,
        Instant applyCloseAt,
        Integer paymentDeadlineMinutes,
        Boolean overnight) {

    public ProgramReviseCommand {
        Objects.requireNonNull(spotId, "spotId");
    }
}
