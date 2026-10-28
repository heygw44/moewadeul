package com.moewadeul.program.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.program.application.catalog.ProgramPage;
import com.moewadeul.program.application.catalog.ProgramSummary;
import java.time.Instant;
import java.util.List;

public record ProgramPageResponse(
        List<ProgramSummaryResponse> content,
        int page,
        int size,
        boolean hasNext,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant nextApplyOpenAt) {

    static ProgramPageResponse from(ProgramPage page) {
        List<ProgramSummaryResponse> content =
                page.content().stream().map(ProgramSummaryResponse::from).toList();
        return new ProgramPageResponse(content, page.page(), page.size(), page.hasNext(), page.nextApplyOpenAt());
    }

    public record ProgramSummaryResponse(
            long programId,
            String title,
            String locationText,
            Long spotId,
            Instant startAt,
            Instant endAt,
            Instant applyOpenAt,
            Instant applyCloseAt,
            int capacity,
            int remainingSeats,
            int fee,
            boolean overnight,
            String status) {

        static ProgramSummaryResponse from(ProgramSummary summary) {
            return new ProgramSummaryResponse(
                    summary.programId(),
                    summary.title(),
                    summary.locationText(),
                    summary.spotId(),
                    summary.startAt(),
                    summary.endAt(),
                    summary.applyOpenAt(),
                    summary.applyCloseAt(),
                    summary.capacity(),
                    summary.remainingSeats(),
                    summary.fee(),
                    summary.overnight(),
                    summary.status());
        }
    }
}
