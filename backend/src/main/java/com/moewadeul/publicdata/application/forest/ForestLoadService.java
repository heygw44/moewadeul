package com.moewadeul.publicdata.application.forest;

import com.moewadeul.publicdata.application.SyncJob;
import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.forest.ForestFile;
import com.moewadeul.publicdata.infra.forest.ForestFileReader;
import com.moewadeul.publicdata.infra.forest.ForestRecord;
import com.moewadeul.spot.application.publicspot.PublicSpotCommand;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncService;
import com.moewadeul.spot.application.publicspot.PublicSpotUpsertResult;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForestLoadService implements SyncJob<ForestLoadResult> {

    private final SyncJobRunService syncJobRunService;
    private final ForestFileReader forestFileReader;
    private final PublicSpotSyncService publicSpotSyncService;
    private final ForestLoadProperties properties;

    public Optional<ForestLoadResult> load() {
        return load(properties.file());
    }

    Optional<ForestLoadResult> load(Path file) {
        return syncJobRunService
                .begin(SyncJobType.FOREST, properties.staleAfter())
                .map(start -> run(start, file));
    }

    @Override
    public SyncJobType type() {
        return SyncJobType.FOREST;
    }

    @Override
    public Duration staleAfter() {
        return properties.staleAfter();
    }

    @Override
    public ForestLoadResult run(SyncJobStart start) {
        return run(start, properties.file());
    }

    private ForestLoadResult run(SyncJobStart start, Path file) {
        long runId = start.runId();
        try {
            ForestLoadResult result = loadFile(runId, requireConfigured(file));
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
                    "휴양림 파일 경로가 설정되지 않았습니다. " + ForestLoadProperties.FILE_PROPERTY + " 값을 확인하세요.");
        }
        return file;
    }

    private ForestLoadResult loadFile(long runId, Path file) {
        log.info("forest load started runId={} file={}", runId, file.getFileName());
        ForestFile forestFile = forestFileReader.read(file);
        Collection<ForestRecord> forests = firstRowPerInstitution(forestFile.records());
        if (forests.isEmpty()) {
            throw new IllegalStateException("휴양림 파일에 휴양림이 없습니다. file=" + file.getFileName());
        }
        List<PublicSpotCommand> commands = toCommands(forests, forestFile.sourceDate());
        PublicSpotUpsertResult upserted = publicSpotSyncService.upsert(PublicSpotSource.FOREST, commands);
        int removed = publicSpotSyncService.markMissingRemoved(PublicSpotSource.FOREST, institutionIds(forests));
        ForestLoadResult result = ForestLoadResult.of(
                forestFile.sourceDate(), forests.size(), forests.size() - commands.size(), upserted, removed);
        syncJobRunService.recordProgress(runId, null, result.processedCount(), result.skipped());
        return result;
    }

    private static Collection<ForestRecord> firstRowPerInstitution(List<ForestRecord> records) {
        Map<String, ForestRecord> byInstitutionId = new LinkedHashMap<>();
        for (ForestRecord record : records) {
            byInstitutionId.putIfAbsent(record.institutionId().strip(), record);
        }
        return byInstitutionId.values();
    }

    private static List<String> institutionIds(Collection<ForestRecord> forests) {
        return forests.stream().map(ForestRecord::institutionId).toList();
    }

    private static List<PublicSpotCommand> toCommands(Collection<ForestRecord> forests, LocalDate sourceDate) {
        List<PublicSpotCommand> commands = new ArrayList<>();
        for (ForestRecord forest : forests) {
            toCommand(forest, sourceDate).ifPresent(commands::add);
        }
        return commands;
    }

    private static Optional<PublicSpotCommand> toCommand(ForestRecord forest, LocalDate sourceDate) {
        try {
            return Optional.of(new PublicSpotCommand(
                    forest.institutionId(),
                    forest.name(),
                    Double.parseDouble(forest.latitude().strip()),
                    Double.parseDouble(forest.longitude().strip()),
                    null,
                    null,
                    null,
                    forest.phone(),
                    forest.homepage(),
                    null,
                    null,
                    null,
                    sourceDate,
                    Set.of()));
        } catch (NumberFormatException e) {
            log.warn(
                    "forest skipped externalId={} reason=invalid coordinate: {}",
                    forest.institutionId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private void recordFailure(long runId, RuntimeException cause) {
        log.warn("forest load failed runId={} error={}", runId, cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(long runId, ForestLoadResult result) {
        log.info(
                "forest load completed runId={} sourceDate={} processed={} inserted={} updated={} unchanged={} removed={} skipped={}",
                runId,
                result.sourceDate(),
                result.processedCount(),
                result.inserted(),
                result.updated(),
                result.unchanged(),
                result.removed(),
                result.skipped());
    }
}
