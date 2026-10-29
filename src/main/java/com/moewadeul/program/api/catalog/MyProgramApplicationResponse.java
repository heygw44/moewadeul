package com.moewadeul.program.api.catalog;

import com.moewadeul.program.application.catalog.MyProgramApplicationItem;
import com.moewadeul.program.application.catalog.MyProgramApplicationPage;
import java.time.Instant;
import java.util.List;

public record MyProgramApplicationPageResponse(List<ItemResponse> content, int page, int size, boolean hasNext) {

    static MyProgramApplicationPageResponse from(MyProgramApplicationPage page) {
        List<ItemResponse> content =
                page.content().stream().map(ItemResponse::from).toList();
        return new MyProgramApplicationPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record ItemResponse(
            long applicationId,
            String status,
            Instant paymentDueAt,
            Instant confirmedAt,
            Instant canceledAt,
            String cancelReason,
            Instant createdAt,
            ProgramResponse program) {

        static ItemResponse from(MyProgramApplicationItem item) {
            MyProgramApplicationItem.Program program = item.program();
            return new ItemResponse(
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
