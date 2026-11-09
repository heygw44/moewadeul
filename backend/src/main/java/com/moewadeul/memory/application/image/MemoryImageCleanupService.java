package com.moewadeul.memory.application.image;

import com.moewadeul.common.image.application.ImageCleanup;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MemoryImageCleanupService extends ImageCleanup {

    private final MemoryImageMapper memoryImageMapper;

    MemoryImageCleanupService(MemoryImageMapper memoryImageMapper, ImageStorage imageStorage, Clock clock) {
        super(MemoryImage.KIND, memoryImageMapper, imageStorage, clock);
        this.memoryImageMapper = memoryImageMapper;
    }

    public Result deleteObjectsThenRowsOf(long memberId) {
        OutsideTransaction.require("저장소 삭제를 기다리는 동안 DB 커넥션과 행 잠금을 쥐면 안 된다.");
        Result result = deleteObjectsThenRowsByPage(
                afterId -> memoryImageMapper.selectUnattachedByMember(memberId, afterId, BATCH_SIZE));
        log.info(
                "memory image cleanup memberId={} deletedRows={} storageFailures={}",
                memberId,
                result.deletedRows(),
                result.storageFailures());
        return result;
    }
}
