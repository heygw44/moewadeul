package com.moewadeul.program.domain;

import com.moewadeul.common.patch.PatchField;
import java.time.Instant;

public record ProgramRevision(
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

    public ProgramRevision {
        spotId = spotId == null ? PatchField.absent() : spotId;
    }
}
