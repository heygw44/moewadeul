package com.moewadeul.publicdata.application.forest;

import com.moewadeul.spot.application.publicspot.PublicSpotUpsertResult;
import java.time.LocalDate;

public record ForestStandardLoadResult(
        LocalDate sourceDate,
        int processedCount,
        int groupCount,
        int inserted,
        int updated,
        int unchanged,
        int removed,
        int skippedNoLodging,
        int skippedInvalid,
        int skippedNational,
        int rejected) {

    static ForestStandardLoadResult of(
            LocalDate sourceDate,
            int processedCount,
            ForestStandardMatcher.Result matched,
            PublicSpotUpsertResult upserted,
            int removed) {
        return new ForestStandardLoadResult(
                sourceDate,
                processedCount,
                matched.groupCount(),
                upserted.inserted(),
                upserted.updated(),
                upserted.unchanged(),
                removed,
                matched.skippedNoLodging(),
                matched.skippedInvalid(),
                matched.skippedNational(),
                upserted.skipped());
    }

    int skipped() {
        return skippedNoLodging + skippedInvalid + skippedNational + rejected;
    }
}
