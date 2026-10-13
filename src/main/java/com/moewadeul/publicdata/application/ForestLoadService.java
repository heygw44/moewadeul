package com.moewadeul.publicdata.application;

import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.publicdata.infra.ForestFile;
import com.moewadeul.publicdata.infra.ForestFileReader;
import com.moewadeul.publicdata.infra.ForestRecord;
import com.moewadeul.spot.application.PublicSpotCommand;
import com.moewadeul.spot.application.PublicSpotSource;
import com.moewadeul.spot.application.PublicSpotSyncService;
import com.moewadeul.spot.application.PublicSpotUpsertResult;
import java.nio.file.Path;
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

/**
 * 국립자연휴양림 예약 정책 파일을 읽어 휴양림을 장소로 적재하고, 파일 기준일을 장소 상세에 남긴다.
 *
 * <p>이 파일은 휴양림 목록이 아니라 예약 정책 목록이다. 예약 정책마다 한 행이라 같은 휴양림이 여러 행에 반복되고, 이름·좌표·연락처·홈페이지는 모든
 * 행에서 같다. 그래서 서비스는 기관아이디마다 처음 나온 행 하나만 적재한다.
 *
 * <p>새 파일에 기관아이디가 없는 기존 휴양림은 고캠핑의 원천 삭제와 같은 방식으로 숨기고, 숨긴 시각을 남긴다. 그 휴양림이 파일에 다시 나타나면
 * 되돌린다. 관리자가 숨긴 휴양림은 숨긴 시각이 없어서 숨기지도 되돌리지도 않는다. 좌표가 잘못돼 건너뛴 행도 파일에는 있으므로 숨기지 않는다.
 * 휴양림이 하나도 없는 파일은 읽기에 실패한 것으로 보고, 아무것도 숨기지 않고 실행을 실패로 끝낸다.
 *
 * <p>서비스는 파일 전체를 장소 모듈의 트랜잭션 하나로 적재한다. 그래서 적재 도중 실패하면 DB가 그 트랜잭션을 모두 되돌리고, 다음 실행이 이어서
 * 처리할 위치도 없다. 따라서 서비스는 실행 기록에 진행 위치를 쓰지 않고, 직전 실행이 실패했어도 파일을 처음부터 다시 적재한다. 빠진 휴양림
 * 숨기기는 적재를 커밋한 뒤 다른 트랜잭션에서 한다. 그래서 숨기기만 실패하면 적재 결과는 남고 실행은 실패로 기록된다. 이때 같은 파일로 다시
 * 실행하면 적재는 바뀐 것이 없고 숨기기를 다시 한다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록과 적재는 각 서비스의 트랜잭션에서 따로 커밋한다. 그래서 적재가 실패해도 실패한 실행 기록은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForestLoadService {

    private final SyncJobRunService syncJobRunService;
    private final ForestFileReader forestFileReader;
    private final PublicSpotSyncService publicSpotSyncService;
    private final ForestLoadProperties properties;

    /**
     * 호출하면 설정한 경로의 휴양림 파일을 한 번 적재하고 결과를 돌려준다. 다른 적재가 진행 중이라 시작하지 않았으면 빈 값을 돌려준다.
     * 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public Optional<ForestLoadResult> load() {
        return load(properties.file());
    }

    Optional<ForestLoadResult> load(Path file) {
        return syncJobRunService
                .begin(SyncJobType.FOREST, properties.staleAfter())
                .map(start -> run(start, file));
    }

    /**
     * 호출하면 이미 시작한 실행 기록으로 설정한 경로의 휴양림 파일을 적재하고 결과를 돌려준다. 실행 기록은 호출하는 쪽이
     * {@link SyncJobRunService#begin}으로 먼저 만들어 넘긴다. 성공하면 실행 기록을 COMPLETED로, 실패하면 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
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

    // 경로가 비었는지는 실행을 시작한 뒤에 확인한다. 그래야 설정을 빠뜨린 실행도 실패 기록으로 남는다.
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

    // 좌표를 읽지 못한 휴양림 하나 때문에 나머지 휴양림까지 적재하지 않을 이유는 없다. 그래서 서비스는 그 휴양림만 건너뛴다.
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

    // 예외 메시지에는 파일 경로와 줄 번호 같은 값만 들어가고 비밀값은 없다. 그래서 메시지를 그대로 실행 기록에 남긴다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
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
