package com.moewadeul.publicdata.application.park;

import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.park.ParkBoundaryFile;
import com.moewadeul.publicdata.infra.park.ParkBoundaryFileReader;
import com.moewadeul.publicdata.infra.park.ParkBoundaryRecord;
import com.moewadeul.spot.application.park.ProtectedAreaCommand;
import com.moewadeul.spot.application.park.ProtectedAreaLoadService;
import com.moewadeul.spot.application.park.ProtectedAreaSource;
import com.moewadeul.spot.application.park.ProtectedAreaType;
import com.moewadeul.spot.application.park.ProtectedAreaUpsertResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParkBoundaryLoadService {

    private final SyncJobRunService syncJobRunService;
    private final ParkBoundaryFileReader parkBoundaryFileReader;
    private final ProtectedAreaLoadService protectedAreaLoadService;
    private final ParkBoundaryLoadProperties properties;

    public Optional<ParkBoundaryLoadResult> load() {
        return load(properties.file());
    }

    public Optional<ParkBoundaryLoadResult> load(Path file) {
        return syncJobRunService
                .begin(SyncJobType.PARK_BOUNDARY, properties.staleAfter())
                .map(start -> run(start, file));
    }

    public ParkBoundaryLoadResult run(SyncJobStart start) {
        return run(start, properties.file());
    }

    ParkBoundaryLoadResult run(SyncJobStart start, Path file) {
        long runId = start.runId();
        try {
            ParkBoundaryLoadResult result = loadFile(runId, requireConfigured(file));
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
                    "공원 경계 파일 경로가 설정되지 않았습니다. " + ParkBoundaryLoadProperties.FILE_PROPERTY + " 값을 확인하세요.");
        }
        return file;
    }

    private ParkBoundaryLoadResult loadFile(long runId, Path file) {
        log.info("park boundary load started runId={} file={}", runId, file.getFileName());
        ParkBoundaryFile boundaryFile = parkBoundaryFileReader.read(file);
        List<ProtectedAreaCommand> commands = toCommands(boundaryFile.records());
        ProtectedAreaUpsertResult upserted =
                protectedAreaLoadService.upsert(ProtectedAreaSource.KDPA, boundaryFile.sourceDate(), commands);
        ParkBoundaryLoadResult result = ParkBoundaryLoadResult.of(boundaryFile.sourceDate(), commands.size(), upserted);
        syncJobRunService.recordProgress(runId, null, result.processedCount(), 0);
        return result;
    }

    private static List<ProtectedAreaCommand> toCommands(List<ParkBoundaryRecord> records) {
        return records.stream()
                .map(record -> new ProtectedAreaCommand(
                        record.name(), ProtectedAreaType.valueOf(record.areaType()), record.multiPolygonWkt()))
                .toList();
    }

    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "park boundary load failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static void logCompleted(long runId, ParkBoundaryLoadResult result) {
        log.info(
                "park boundary load completed runId={} sourceDate={} processed={} inserted={} updated={}",
                runId,
                result.sourceDate(),
                result.processedCount(),
                result.inserted(),
                result.updated());
    }
}
