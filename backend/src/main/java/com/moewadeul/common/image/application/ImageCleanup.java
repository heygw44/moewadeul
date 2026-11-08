package com.moewadeul.common.image.application;

import com.moewadeul.common.image.domain.AttachableImage;
import com.moewadeul.common.image.domain.ImageKind;
import com.moewadeul.common.image.infra.AttachableImageMapper;
import com.moewadeul.common.image.infra.UnattachedImageRow;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ImageCleanup {

    public static final int BATCH_SIZE = 100;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String keyPrefix;
    private final AttachableImageMapper imageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    protected ImageCleanup(ImageKind kind, AttachableImageMapper imageMapper, ImageStorage imageStorage, Clock clock) {
        this.keyPrefix = kind.keyPrefix();
        this.imageMapper = imageMapper;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    // 행을 먼저 지우면 저장소 삭제가 실패한 객체를 다시 찾을 수 없다. 추억 사진은 개인정보라 객체부터 지우고 성공한 사진의 행만 지운다.
    public Result deleteExpiredObjectsThenRows() {
        OutsideTransaction.require("저장소 삭제를 기다리는 동안 DB 커넥션과 행 잠금을 쥐면 안 된다.");
        Instant createdBefore = clock.instant().minus(AttachableImage.UNATTACHED_RETENTION);
        Result result = deleteObjectsThenRowsByPage(
                afterId -> imageMapper.selectUnattachedCreatedBefore(createdBefore, afterId, BATCH_SIZE));
        log.info(
                "{} image cleanup deletedRows={} storageFailures={}",
                keyPrefix,
                result.deletedRows(),
                result.storageFailures());
        return result;
    }

    // 지우지 못한 사진은 id 뒤로 넘어가 다음 쪽을 읽는다. 다시 처음부터 읽으면 계속 실패하는 사진이 한 쪽을 채웠을 때 정리가 멈춘다.
    protected Result deleteObjectsThenRowsByPage(LongFunction<List<UnattachedImageRow>> pageAfterId) {
        int deletedRows = 0;
        int storageFailures = 0;
        long lastImageId = 0;
        List<UnattachedImageRow> page;
        do {
            page = pageAfterId.apply(lastImageId);
            if (page.isEmpty()) {
                break;
            }
            Set<Long> failed = deleteObjectsReturningFailedIds(page);
            storageFailures += failed.size();
            List<Long> deletable = page.stream()
                    .map(UnattachedImageRow::imageId)
                    .filter(imageId -> !failed.contains(imageId))
                    .toList();
            if (!deletable.isEmpty()) {
                deletedRows += imageMapper.deleteUnattachedByIds(deletable);
            }
            lastImageId = page.getLast().imageId();
        } while (page.size() == BATCH_SIZE);
        return new Result(deletedRows, storageFailures);
    }

    private Set<Long> deleteObjectsReturningFailedIds(List<UnattachedImageRow> rows) {
        List<String> keys = rows.stream().flatMap(ImageCleanup::keysOf).toList();
        if (keys.isEmpty()) {
            return Set.of();
        }
        Set<String> failedKeys = imageStorage.deleteReturningFailedKeys(keys);
        Set<Long> failedIds = rows.stream()
                .filter(row -> keysOf(row).anyMatch(failedKeys::contains))
                .map(UnattachedImageRow::imageId)
                .collect(Collectors.toSet());
        if (!failedIds.isEmpty()) {
            log.warn("{} image object delete failed images={} keys={}", keyPrefix, failedIds.size(), failedKeys.size());
        }
        return failedIds;
    }

    private static Stream<String> keysOf(UnattachedImageRow row) {
        return row.thumbnailKey() == null ? Stream.of(row.objectKey()) : Stream.of(row.objectKey(), row.thumbnailKey());
    }

    public record Result(int deletedRows, int storageFailures) {}
}
