package com.moewadeul.publicdata.application.gocamping;

import com.moewadeul.publicdata.application.SyncJob;
import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.gocamping.GoCampingClient;
import com.moewadeul.publicdata.infra.gocamping.GoCampingItem;
import com.moewadeul.publicdata.infra.gocamping.GoCampingPage;
import com.moewadeul.publicdata.infra.gocamping.GoCampingProperties;
import com.moewadeul.spot.application.publicspot.PublicSpotCommand;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncService;
import com.moewadeul.spot.application.publicspot.PublicSpotUpsertResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoCampingSyncService implements SyncJob<GoCampingSyncResult> {

    private static final PublicSpotUpsertResult NOTHING_UPSERTED = new PublicSpotUpsertResult(0, 0, 0, 0);

    private final SyncJobRunService syncJobRunService;
    private final GoCampingClient goCampingClient;
    private final PublicSpotSyncService publicSpotSyncService;
    private final GoCampingProperties goCampingProperties;
    private final GoCampingSyncProperties syncProperties;

    public Optional<GoCampingSyncResult> sync() {
        return syncJobRunService
                .begin(SyncJobType.GOCAMPING, syncProperties.staleAfter())
                .map(this::run);
    }

    @Override
    public SyncJobType type() {
        return SyncJobType.GOCAMPING;
    }

    @Override
    public Duration staleAfter() {
        return syncProperties.staleAfter();
    }

    @Override
    public GoCampingSyncResult run(SyncJobStart start) {
        long runId = start.runId();
        try {
            int firstPage = firstPage(start.resumeCursor());
            log.info("gocamping sync started runId={} firstPage={}", runId, firstPage);
            GoCampingSyncResult result = syncPages(runId, firstPage);
            syncJobRunService.complete(runId);
            logCompleted(result);
            return result;
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    private static int firstPage(String resumeCursor) {
        if (resumeCursor == null) {
            return 1;
        }
        return Integer.parseInt(resumeCursor) + 1;
    }

    private GoCampingSyncResult syncPages(long runId, int firstPage) {
        int pageSize = goCampingProperties.pageSize();
        GoCampingSyncResult result = GoCampingSyncResult.empty(runId);
        int pageNo = firstPage;
        while (true) {
            GoCampingPage page = goCampingClient.fetchPage(pageNo, pageSize);
            if (page.items().isEmpty()) {
                return result;
            }
            GoCampingSyncResult afterPage = syncPage(result, page.items());
            syncJobRunService.recordProgress(
                    runId, String.valueOf(pageNo), page.items().size(), afterPage.skipped() - result.skipped());
            result = afterPage;
            if ((long) pageNo * pageSize >= page.totalCount()) {
                return result;
            }
            pageNo++;
        }
    }

    private GoCampingSyncResult syncPage(GoCampingSyncResult result, List<GoCampingItem> items) {
        Map<Boolean, List<GoCampingItem>> byRemoved =
                items.stream().collect(Collectors.partitioningBy(GoCampingItemConverter::isRemoved));
        List<GoCampingItem> liveItems = byRemoved.get(false);
        List<PublicSpotCommand> commands = liveItems.stream()
                .map(GoCampingItemConverter::convert)
                .flatMap(Optional::stream)
                .toList();
        List<String> removedIds = byRemoved.get(true).stream()
                .map(GoCampingItem::contentId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::strip)
                .toList();
        int skipped = liveItems.size() - commands.size() + (byRemoved.get(true).size() - removedIds.size());
        PublicSpotUpsertResult upserted = commands.isEmpty()
                ? NOTHING_UPSERTED
                : publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, commands);
        int hidden =
                removedIds.isEmpty() ? 0 : publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, removedIds);
        return result.addPage(items.size(), skipped, upserted, hidden);
    }

    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "gocamping sync failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(GoCampingSyncResult result) {
        log.info(
                "gocamping sync completed runId={} pages={} processed={} inserted={} updated={} unchanged={} skipped={} removed={}",
                result.runId(),
                result.pages(),
                result.processedCount(),
                result.inserted(),
                result.updated(),
                result.unchanged(),
                result.skipped(),
                result.removed());
    }
}
