package com.moewadeul.publicdata.application.park;

import com.moewadeul.spot.application.park.ProtectedAreaUpsertResult;
import java.time.LocalDate;

public record ParkBoundaryLoadResult(LocalDate sourceDate, int processedCount, int inserted, int updated) {

    static ParkBoundaryLoadResult of(LocalDate sourceDate, int processedCount, ProtectedAreaUpsertResult upserted) {
        return new ParkBoundaryLoadResult(sourceDate, processedCount, upserted.inserted(), upserted.updated());
    }
}
