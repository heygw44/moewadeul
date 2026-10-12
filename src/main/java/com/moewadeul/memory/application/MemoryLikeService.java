package com.moewadeul.memory.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.infra.MemoryLikeMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 공개 추억 기록에 좋아요를 누르거나 취소한다.
 *
 * <p>기록이 없거나 비공개이면 작성자 본인에게도 NOT_FOUND로 거부한다. 공개 기록이면 작성자 본인도 누를 수 있다. 이미 누른 기록에 다시 누르거나
 * 누르지 않은 기록을 취소해도 예외 없이 같은 결과를 돌려주므로, 호출하는 쪽이 같은 요청을 다시 보내도 된다. 기록을 비공개로 바꿔도 좋아요 행은
 * 지우지 않아서, 다시 공개하면 좋아요 수가 돌아온다.
 */
@Service
@RequiredArgsConstructor
public class MemoryLikeService {

    private final MemoryRepository memoryRepository;
    private final MemoryLikeMapper memoryLikeMapper;
    private final Clock clock;

    /** 호출하면 memberId인 회원의 좋아요를 memoryId인 기록에 남기고, 그 뒤의 상태를 돌려준다. */
    @Transactional
    public MemoryLikeResult like(long memberId, long memoryId) {
        requirePublicMemory(memoryId);
        memoryLikeMapper.insertIgnoringDuplicate(memoryId, memberId, clock.instant());
        return new MemoryLikeResult(true, memoryLikeMapper.countByMemory(memoryId));
    }

    /** 호출하면 memberId인 회원의 좋아요를 memoryId인 기록에서 지우고, 그 뒤의 상태를 돌려준다. */
    @Transactional
    public MemoryLikeResult unlike(long memberId, long memoryId) {
        requirePublicMemory(memoryId);
        memoryLikeMapper.delete(memoryId, memberId);
        return new MemoryLikeResult(false, memoryLikeMapper.countByMemory(memoryId));
    }

    private void requirePublicMemory(long memoryId) {
        memoryRepository
                .findById(memoryId)
                .filter(Memory::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
