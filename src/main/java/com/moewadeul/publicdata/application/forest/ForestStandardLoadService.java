package com.moewadeul.publicdata.application.forest;

import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.application.forest.ForestStandardMatcher.Forest;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.forest.ForestStandardFile;
import com.moewadeul.publicdata.infra.forest.ForestStandardFileReader;
import com.moewadeul.publicdata.infra.forest.ForestStandardRecord;
import com.moewadeul.spot.application.publicspot.PublicSpotCommand;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncService;
import com.moewadeul.spot.application.publicspot.PublicSpotUpsertResult;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForestStandardLoadService {

    private final SyncJobRunService syncJobRunService;
    private final ForestStandardFileReader forestStandardFileReader;
    private final PublicSpotSyncService publicSpotSyncService;
    private final ForestStandardLoadProperties properties;

    public Optional<ForestStandardLoadResult> load() {
        return load(properties.file());
    }

    Optional<ForestStandardLoadResult> load(Path file) {
        return syncJobRunService
                .begin(SyncJobType.FOREST_STANDARD, properties.staleAfter())
                .map(start -> run(start, file));
    }

    public ForestStandardLoadResult run(SyncJobStart start) {
        return run(start, properties.file());
    }

    private ForestStandardLoadResult run(SyncJobStart start, Path file) {
        long runId = start.runId();
        try {
            ForestStandardLoadResult result = loadFile(runId, requireConfigured(file));
            syncJobRunService.complete(runId);
            logCompleted(runId, result);
            return result;
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    private static Path requireConfigured(Path file) {
        if (file == null) {
            throw new IllegalStateException(
                    "휴양림 표준데이터 파일 경로가 설정되지 않았습니다. " + ForestStandardLoadProperties.FILE_PROPERTY + " 값을 확인하세요.");
        }
        return file;
    }

    private ForestStandardLoadResult loadFile(long runId, Path file) {
        log.info("forest standard load started runId={} file={}", runId, file.getFileName());
        ForestStandardFile forestFile = forestStandardFileReader.read(file);
        ForestStandardMatcher.Result matched = ForestStandardMatcher.match(
                forestFile.records(),
                publicSpotSyncService.findLocations(PublicSpotSource.FOREST),
                publicSpotSyncService.findLocations(PublicSpotSource.FOREST_STANDARD));
        if (matched.forests().isEmpty()) {
            throw new IllegalStateException("휴양림 표준데이터 파일에 적재할 휴양림이 없습니다. file=" + file.getFileName()
                    + ", rows=" + forestFile.records().size()
                    + ", skippedNoLodging=" + matched.skippedNoLodging()
                    + ", skippedInvalid=" + matched.skippedInvalid()
                    + ", skippedNational=" + matched.skippedNational());
        }
        List<PublicSpotCommand> commands = matched.forests().stream()
                .map(ForestStandardLoadService::toCommand)
                .toList();
        PublicSpotUpsertResult upserted = publicSpotSyncService.upsert(PublicSpotSource.FOREST_STANDARD, commands);
        int removed = publicSpotSyncService.markMissingRemoved(
                PublicSpotSource.FOREST_STANDARD,
                matched.forests().stream().map(Forest::externalId).toList());
        ForestStandardLoadResult result = ForestStandardLoadResult.of(
                latestSourceDate(matched.forests()), forestFile.records().size(), matched, upserted, removed);
        syncJobRunService.recordProgress(runId, null, result.processedCount(), result.skipped());
        return result;
    }

    private static PublicSpotCommand toCommand(Forest forest) {
        ForestStandardMatcher.Row row = forest.representative();
        ForestStandardRecord record = row.record();
        return new PublicSpotCommand(
                forest.externalId(),
                record.name().strip(),
                row.latitude(),
                row.longitude(),
                blankToNull(record.address()),
                null,
                null,
                blankToNull(record.phone()),
                blankToNull(record.homepage()),
                null,
                null,
                null,
                row.sourceDate(),
                Set.of());
    }

    private static String blankToNull(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static LocalDate latestSourceDate(List<Forest> forests) {
        return forests.stream()
                .map(forest -> forest.representative().sourceDate())
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "forest standard load failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(long runId, ForestStandardLoadResult result) {
        log.info(
                "forest standard load completed runId={} sourceDate={} processed={} groups={} inserted={} updated={} unchanged={} removed={} skippedNoLodging={} skippedInvalid={} skippedNational={} rejected={}",
                runId,
                result.sourceDate(),
                result.processedCount(),
                result.groupCount(),
                result.inserted(),
                result.updated(),
                result.unchanged(),
                result.removed(),
                result.skippedNoLodging(),
                result.skippedInvalid(),
                result.skippedNational(),
                result.rejected());
    }
}
