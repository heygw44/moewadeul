package com.moewadeul.program.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.program.application.catalog.ProgramDetail;
import java.time.Instant;

public record ProgramDetailResponse(
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
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) MyApplicationResponse myApplication) {

    static ProgramDetailResponse from(ProgramDetail detail) {
        ProgramDetail.MyApplication mine = detail.myApplication();
        MyApplicationResponse myApplication = mine == null
                ? null
                : new MyApplicationResponse(mine.applicationId(), mine.status(), mine.paymentDueAt());
        return new ProgramDetailResponse(
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
                detail.status(),
                myApplication);
    }

    public record MyApplicationResponse(long applicationId, String status, Instant paymentDueAt) {}
}
