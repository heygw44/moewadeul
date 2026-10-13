package com.moewadeul.publicdata.application;

import com.moewadeul.publicdata.application.ForestStandardMatcher.Forest;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.ForestStandardFile;
import com.moewadeul.publicdata.infra.ForestStandardFileReader;
import com.moewadeul.publicdata.infra.ForestStandardRecord;
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

/**
 * 전국휴양림표준데이터 JSON 파일을 읽어 공립·사립 휴양림을 장소로 적재한다.
 *
 * <p>이 파일에는 고유 ID가 없고 중복 행과 국립자연휴양림과 겹치는 행이 섞여 있다. 그래서 서비스는 {@link ForestStandardMatcher}로 행을 휴양림 단위로
 * 묶고, 국립과 겹치는 휴양림을 빼고, 이미 저장한 장소와 위치로 맞춰 외부 ID를 정한 뒤 적재한다. 국립 파일을 먼저 적재해야 겹침을 가릴 수 있다.
 * 국립 적재와 이 적재를 동시에 실행했을 때의 경합은 다루지 않는다.
 *
 * <p>이번 파일의 휴양림에 없는 기존 표준데이터 휴양림은 원천 삭제와 같은 방식으로 숨기고 숨긴 시각을 남긴다. 나중에 국립 파일이 들어와 겹치게
 * 된 휴양림도 같은 방식으로 숨겨진다. 숨긴 휴양림이 다시 파일에 나타나면 되돌린다. 관리자가 숨긴 휴양림은 숨긴 시각이 없어서 숨기지도 되돌리지도
 * 않는다.
 *
 * <p>적재할 휴양림이 하나도 남지 않는 파일(숙박 가능한 행이 없거나, 모두 잘못된 행이거나, 모두 국립과 겹치는 파일)은 읽기에 실패한 것으로 보고, 아무것도
 * 숨기지 않고 실행을 실패로 끝낸다.
 *
 * <p>서비스는 파일 전체를 장소 모듈의 트랜잭션 하나로 적재하므로, 적재 도중 실패하면 DB가 모두 되돌리고 실행 기록에 진행 위치를 쓰지 않는다. 빠진
 * 휴양림 숨기기는 적재를 커밋한 뒤 다른 트랜잭션에서 한다. 그래서 숨기기만 실패하면 적재 결과는 남고 실행은 실패로 기록되며, 같은 파일로 다시
 * 실행하면 숨기기를 다시 한다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록과 적재는 각 서비스의 트랜잭션에서 따로 커밋한다. 그래서 적재가 실패해도 실패한 실행 기록은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForestStandardLoadService {

    private final SyncJobRunService syncJobRunService;
    private final ForestStandardFileReader forestStandardFileReader;
    private final PublicSpotSyncService publicSpotSyncService;
    private final ForestStandardLoadProperties properties;

    /**
     * 호출하면 설정한 경로의 휴양림 파일을 한 번 적재하고 결과를 돌려준다. 다른 적재가 진행 중이라 시작하지 않았으면 빈 값을 돌려준다.
     * 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public Optional<ForestStandardLoadResult> load() {
        return load(properties.file());
    }

    Optional<ForestStandardLoadResult> load(Path file) {
        return syncJobRunService
                .begin(SyncJobType.FOREST_STANDARD, properties.staleAfter())
                .map(start -> run(start, file));
    }

    /**
     * 호출하면 이미 시작한 실행 기록으로 설정한 경로의 휴양림 파일을 적재하고 결과를 돌려준다. 실행 기록은 호출하는 쪽이
     * {@link SyncJobRunService#begin}으로 먼저 만들어 넘긴다. 성공하면 실행 기록을 COMPLETED로, 실패하면 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
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

    // 경로가 비었는지는 실행을 시작한 뒤에 확인한다. 그래야 설정을 빠뜨린 실행도 실패 기록으로 남는다.
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

    // 예외 메시지에는 파일 이름과 건수 같은 값만 들어가고 비밀값은 없다. 그래서 메시지를 그대로 실행 기록에 남긴다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
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
