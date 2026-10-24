package com.moewadeul.memory.application;

import com.moewadeul.memory.application.image.MemoryImageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 탈퇴한 회원의 추억 기록과 사진을 모두 지운다. 다른 콘텐츠와 달리 기록은 개인의 위치와 사진이라서 탈퇴 뒤에 남기지 않는다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 기록 행은 {@link MemoryWriter}의 트랜잭션 하나로 지우고, 사진은 저장소 삭제가 느릴 수 있어서
 * {@link MemoryImageCleanupService}가 트랜잭션 없이 한 장씩 지운다. 두 단계 모두 같은 회원으로 다시 불러도 결과가 같아서, 중간에 실패해
 * 이벤트가 다시 와도 남은 것만 지운다.
 *
 * <p>저장소 객체를 지우지 못한 사진이 있으면 예외를 던져 이벤트를 실패로 돌려보낸다. 그 사진의 행은 남아 있으므로, 이벤트를 다시 처리할 때
 * 남은 객체를 다시 지운다. 재시도 한도를 넘기면 이벤트가 실패 상태로 남아 운영자가 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWithdrawalCleanupService {

    private final MemoryWriter memoryWriter;
    private final MemoryImageCleanupService memoryImageCleanupService;

    /**
     * 호출하면 memberId인 회원의 기록을 모두 지운 뒤, 그 회원이 올린 사진을 저장소 객체와 행까지 모두 지운다. 객체를 지우지 못한 사진이 있으면
     * 그 행을 남기고 {@link IllegalStateException}을 던진다.
     */
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
