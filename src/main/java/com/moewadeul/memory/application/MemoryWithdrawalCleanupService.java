package com.moewadeul.memory.application;

import com.moewadeul.memory.application.image.MemoryImageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWithdrawalCleanupService {

    private final MemoryWriter memoryWriter;
    private final MemoryImageCleanupService memoryImageCleanupService;

    public void cleanUp(long memberId) {
        int deletedMemories = memoryWriter.deleteAllOf(memberId);
        MemoryImageCleanupService.Result images = memoryImageCleanupService.deleteUnattachedOf(memberId);
        log.info("memory withdrawal cleanup memberId={} deletedMemories={}", memberId, deletedMemories);
        if (images.storageFailures() > 0) {
            throw new IllegalStateException(
                    "탈퇴한 회원의 추억 기록 사진 객체를 지우지 못했습니다. memberId=" + memberId + ", failures=" + images.storageFailures());
        }
    }
}
