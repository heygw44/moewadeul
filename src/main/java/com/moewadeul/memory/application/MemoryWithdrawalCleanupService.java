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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWithdrawalCleanupService {

    private final MemoryWriter memoryWriter;
    private final MemoryImageCleanupService memoryImageCleanupService;

    /** 호출하면 memberId인 회원의 기록을 모두 지운 뒤, 그 회원이 올린 사진을 행과 저장소 객체까지 모두 지운다. */
    public void cleanUp(long memberId) {
        int deletedMemories = memoryWriter.deleteAllOf(memberId);
        memoryImageCleanupService.deleteUnattachedOf(memberId);
        log.info("memory withdrawal cleanup memberId={} deletedMemories={}", memberId, deletedMemories);
    }
}
