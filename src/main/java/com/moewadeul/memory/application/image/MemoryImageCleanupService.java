package com.moewadeul.memory.application.image;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import com.moewadeul.memory.infra.image.MemoryImageRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기록에 붙지 않은 사진을 행과 저장소 객체까지 지운다. 기록에서 뗀 사진과 기록을 지워 떨어진 사진도 같은 대상이다.
 *
 * <p>평소에는 만든 지 24시간이 지난 사진만 지운다. 회원이 탈퇴하면 그 회원의 기록을 먼저 지운 뒤, 그 회원이 올린 붙지 않은 사진을 시간과
 * 상관없이 모두 지운다. 이때는 평소와 반대로 객체를 먼저 지우고 행을 나중에 지운다({@link #deleteUnattachedOf} 참고).
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 저장소 삭제는 느릴 수 있어서 DB 트랜잭션과 행 잠금을 쥔 채 기다리면 안 되기 때문이다. 사진마다 행을
 * 먼저 조건부로 지우고({@code memory_id}가 아직 NULL일 때만), 지웠을 때만 저장소 객체를 지운다. 읽은 뒤 지우기 전에 누군가 그 사진을 기록에
 * 붙였다면 행 삭제가 0건이라서 행과 객체가 모두 남는다. 반대로 객체를 먼저 지우면 기록에 붙은 사진의 파일이 사라질 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryImageCleanupService {

    /** 기록에 붙지 않은 사진을 남겨 두는 시간이다. 만든 지 정확히 이 시간이 된 사진은 남기고, 그보다 오래되면 지운다. */
    static final Duration RETENTION = Duration.ofHours(24);

    // 한 번에 읽는 사진 수의 상한이다. 정리할 사진이 많이 쌓였을 때 한꺼번에 읽지 않고 나눈다.
    static final int BATCH_SIZE = 100;

    private final MemoryImageMapper memoryImageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    /**
     * 호출하면 만든 지 24시간이 지나도록 기록에 붙지 않은 사진을 지운다. 저장소 객체 삭제가 실패하면 경고만 남기고 다음 사진으로 넘어간다. 이때 행은
     * 이미 지워진 상태다.
     */
    public Result deleteUnattached() {
        Instant createdBefore = clock.instant().minus(RETENTION);
        Result result =
                deleteInBatches(() -> memoryImageMapper.selectUnattachedCreatedBefore(createdBefore, BATCH_SIZE));
        log.info(
                "memory image cleanup deletedRows={} storageFailures={}",
                result.deletedRows(),
                result.storageFailures());
        return result;
    }

    /**
     * 호출하면 memberId인 회원이 올렸고 기록에 붙지 않은 사진을 만든 시각과 상관없이 모두 지운다. 탈퇴한 회원의 기록을 지운 뒤에 부른다.
     *
     * <p>{@link #deleteUnattached}와 순서가 반대다. 저장소 객체를 먼저 지우고, 지운 사진의 행만 뒤따라 지운다. 객체를 지우지 못한 사진은
     * 행을 남기고 {@link Result#storageFailures}로 센다. 탈퇴한 회원의 사진은 개인의 위치와 사진이라 반드시 파기해야 하는데, 행이 없으면
     * 그 객체를 다시 찾을 방법이 없기 때문이다. 호출하는 쪽이 실패 수를 보고 같은 회원으로 다시 부르면 남은 사진만 다시 지운다.
     * 탈퇴한 회원은 세션이 모두 지워져 로그인할 수 없으므로 사진을 기록에 새로 붙이는 요청이 없다. 그래서 객체를 먼저 지워도 기록에 붙은 사진의
     * 파일이 사라지지 않는다.
     */
    public Result deleteUnattachedOf(long memberId) {
        int deletedRows = 0;
        int storageFailures = 0;
        long lastImageId = 0;
        List<MemoryImageRow> batch;
        do {
            batch = memoryImageMapper.selectUnattachedByMember(memberId, lastImageId, BATCH_SIZE);
            Result batchResult = deleteObjectsThenRows(batch);
            deletedRows += batchResult.deletedRows();
            storageFailures += batchResult.storageFailures();
            if (!batch.isEmpty()) {
                lastImageId = batch.get(batch.size() - 1).imageId();
            }
        } while (batch.size() == BATCH_SIZE);
        log.info(
                "memory image cleanup memberId={} deletedRows={} storageFailures={}",
                memberId,
                deletedRows,
                storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    // 객체를 지우지 못한 사진은 행을 그대로 둔다. 읽는 쪽이 마지막으로 읽은 ID 뒤부터 이어서 읽으므로, 같은 호출 안에서 같은 사진을 다시 읽지 않는다.
    private Result deleteObjectsThenRows(List<MemoryImageRow> batch) {
        int deletedRows = 0;
        int storageFailures = 0;
        for (MemoryImageRow row : batch) {
            if (!deleteObject(row)) {
                storageFailures++;
                continue;
            }
            deletedRows += memoryImageMapper.deleteIfUnattached(row.imageId());
        }
        return new Result(deletedRows, storageFailures);
    }

    // 한 묶음을 가득 읽었고 그중 하나라도 지웠을 동안 다음 묶음을 읽는다. 하나도 지우지 못한 묶음에서 멈춰야, 다른 요청이 그 사이에 붙인 사진만
    // 남았을 때 같은 묶음을 끝없이 다시 읽지 않는다.
    private Result deleteInBatches(Supplier<List<MemoryImageRow>> nextBatch) {
        int deletedRows = 0;
        int storageFailures = 0;
        int batchDeleted;
        int batchRead;
        do {
            List<MemoryImageRow> batch = nextBatch.get();
            batchRead = batch.size();
            batchDeleted = 0;
            for (MemoryImageRow row : batch) {
                if (memoryImageMapper.deleteIfUnattached(row.imageId()) == 0) {
                    continue;
                }
                batchDeleted++;
                if (!deleteObject(row)) {
                    storageFailures++;
                }
            }
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        return new Result(deletedRows, storageFailures);
    }

    // 원본을 지운 뒤 썸네일이 있으면 썸네일도 지운다. 원본 삭제가 실패해도 썸네일 삭제는 시도하고, 둘 중 하나라도 실패하면 실패로 센다.
    private boolean deleteObject(MemoryImageRow row) {
        boolean deleted = deleteObject(row.imageId(), row.objectKey());
        if (row.thumbnailKey() == null) {
            return deleted;
        }
        return deleteObject(row.imageId(), row.thumbnailKey()) && deleted;
    }

    // 키와 URL에는 회원 ID와 서명이 들어 있어서 로그에 남기지 않고 사진 ID만 남긴다.
    private boolean deleteObject(long imageId, String objectKey) {
        try {
            imageStorage.delete(objectKey);
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "memory image object delete failed imageId={} cause={}",
                    imageId,
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 정리 결과.
     *
     * @param deletedRows 지운 사진 행 수
     * @param storageFailures 저장소 객체를 지우지 못한 사진 수. 평소 정리에서는 행을 이미 지운 사진이고, 탈퇴 정리에서는 행을 남겨 둔 사진이다
     */
    public record Result(int deletedRows, int storageFailures) {}
}
