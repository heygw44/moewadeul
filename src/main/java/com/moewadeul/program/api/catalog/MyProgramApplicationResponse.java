package com.moewadeul.program.api.catalog;

import com.moewadeul.program.application.catalog.MyProgramApplicationItem;
import java.time.Instant;

public record MyProgramApplicationResponse(
        long applicationId,
        String status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        String cancelReason,
        Instant createdAt,
        ProgramResponse program) {

    static MyProgramApplicationResponse from(MyProgramApplicationItem item) {
        MyProgramApplicationItem.Program program = item.program();
        return new MyProgramApplicationResponse(
                item.applicationId(),
                item.status(),
                item.paymentDueAt(),
                item.confirmedAt(),
                item.canceledAt(),
                item.cancelReason(),
                item.createdAt(),
                new ProgramResponse(
                        program.programId(),
                        program.title(),
                        program.locationText(),
                        program.startAt(),
                        program.endAt(),
                        program.fee(),
                        program.status()));
    }

    public record ProgramResponse(
            long programId,
            String title,
            String locationText,
            Instant startAt,
            Instant endAt,
            int fee,
            String status) {}
}
