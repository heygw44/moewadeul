package com.moewadeul.program.application.catalog;

import java.time.Instant;

public record ProgramDetail(
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
        MyApplication myApplication,
        Boolean vacancyAlertSubscribed) {

    public record MyApplication(long applicationId, String status, Instant paymentDueAt) {}
}
