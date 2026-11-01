package com.moewadeul.program.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.program.application.catalog.ProgramPage;
import com.moewadeul.program.application.catalog.ProgramSummary;
import com.moewadeul.program.domain.ProgramPhase;
import java.time.Instant;
import java.util.List;

public record ProgramPageResponse(
        List<ProgramSummaryResponse> content,
        int page,
        int size,
        boolean hasNext,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant nextApplyOpenAt) {

    static ProgramPageResponse from(ProgramPage page) {
        PageSlice<ProgramSummaryResponse> slice = page.slice().map(ProgramSummaryResponse::from);
        return new ProgramPageResponse(
                slice.content(), slice.page(), slice.size(), slice.hasNext(), page.nextApplyOpenAt());
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
            ProgramPhase status) {

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
