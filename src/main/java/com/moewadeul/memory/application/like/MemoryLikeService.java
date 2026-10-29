package com.moewadeul.memory.application.like;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.infra.like.MemoryLikeMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryLikeService {

    private final MemoryRepository memoryRepository;
    private final MemoryLikeMapper memoryLikeMapper;
    private final Clock clock;

    @Transactional
    public MemoryLikeResult like(long memberId, long memoryId) {
        lockVisibleMemory(memoryId);
        memoryLikeMapper.insertIgnoringDuplicate(memoryId, memberId, clock.instant());
        return new MemoryLikeResult(true, memoryLikeMapper.countByMemory(memoryId));
    }

    @Transactional
    public MemoryLikeResult unlike(long memberId, long memoryId) {
        lockVisibleMemory(memoryId);
        memoryLikeMapper.delete(memoryId, memberId);
        return new MemoryLikeResult(false, memoryLikeMapper.countByMemory(memoryId));
    }

    private void lockVisibleMemory(long memoryId) {
        memoryRepository
                .findByIdForUpdate(memoryId)
                .filter(Memory::isVisibleToOthers)
                .orElseThrow(BusinessException::notFound);
    }
}
