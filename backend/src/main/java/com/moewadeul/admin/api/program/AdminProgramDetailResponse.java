package com.moewadeul.admin.api.program;

import com.moewadeul.program.application.catalog.ProgramDetail;
import java.time.Instant;

public record AdminProgramDetailResponse(
        long programId,
        String title,
        String description,
        String locationText,
        Long spotId,
        Instant startAt,
        Instant endAt,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int capacity,
        int remainingSeats,
        int fee,
        int paymentDeadlineMinutes,
        boolean overnight,
        String status) {

    static AdminProgramDetailResponse from(ProgramDetail detail) {
        return new AdminProgramDetailResponse(
                detail.programId(),
                detail.title(),
                detail.description(),
                detail.locationText(),
                detail.spotId(),
                detail.startAt(),
                detail.endAt(),
                detail.applyOpenAt(),
                detail.applyCloseAt(),
                detail.capacity(),
                detail.remainingSeats(),
                detail.fee(),
                detail.paymentDeadlineMinutes(),
                detail.overnight(),
                detail.status());
    }
}
