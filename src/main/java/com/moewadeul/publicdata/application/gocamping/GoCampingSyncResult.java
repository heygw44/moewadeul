package com.moewadeul.publicdata.application.gocamping;

import com.moewadeul.spot.application.publicspot.PublicSpotUpsertResult;

public record GoCampingSyncResult(
        long runId, int pages, int processedCount, int inserted, int updated, int unchanged, int skipped, int removed) {

    static GoCampingSyncResult empty(long runId) {
        return new GoCampingSyncResult(runId, 0, 0, 0, 0, 0, 0, 0);
    }

    GoCampingSyncResult addPage(int itemCount, int convertSkipped, PublicSpotUpsertResult upserted, int hidden) {
        return new GoCampingSyncResult(
                runId,
                pages + 1,
                processedCount + itemCount,
                inserted + upserted.inserted(),
                updated + upserted.updated(),
                unchanged + upserted.unchanged(),
                skipped + convertSkipped + upserted.skipped(),
                removed + hidden);
    }
}
