package com.moewadeul.publicdata.application.forest;

import com.moewadeul.spot.application.publicspot.PublicSpotUpsertResult;
import java.time.LocalDate;

public record ForestLoadResult(
        LocalDate sourceDate, int processedCount, int inserted, int updated, int unchanged, int removed, int skipped) {

    static ForestLoadResult of(
            LocalDate sourceDate, int processedCount, int parseSkipped, PublicSpotUpsertResult upserted, int removed) {
        return new ForestLoadResult(
                sourceDate,
                processedCount,
                upserted.inserted(),
                upserted.updated(),
                upserted.unchanged(),
                removed,
                parseSkipped + upserted.skipped());
    }
}
