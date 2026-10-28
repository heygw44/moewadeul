package com.moewadeul.memory.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryDetails;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.infra.MemoryMapper;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class MemoryWriter {

    private final ActiveSpotChecker activeSpotChecker;
    private final MemoryRepository memoryRepository;
    private final MemoryMapper memoryMapper;
    private final MemoryImageAttacher memoryImageAttacher;
    private final MemoryViewReader memoryViewReader;
    private final Clock clock;

    @Transactional
    public long write(long memberId, MemoryWriteCommand command) {
        Instant now = clock.instant();
        MemoryDetails details = command.toDetails(today(now));
        if (details.spotId() != null) {
            requireLinkable(details.spotId());
        }
        long memoryId = memoryRepository
                .saveAndFlush(Memory.write(memberId, details, now))
                .getId();
        memoryImageAttacher.replace(memberId, memoryId, command.imageIds());
        return memoryId;
    }

    @Transactional
    public MemoryView revise(long memberId, long memoryId, MemoryReviseCommand command) {
        Memory memory = lockOwnMemory(memberId, memoryId);
        Instant now = clock.instant();
        MemoryDetails details = command.mergeInto(memory.details()).toDetails(today(now));
        if (command.linksNewSpot(memory.getSpotId())) {
            requireLinkable(details.spotId());
        }
        memory.revise(details, now);
        if (command.imageIds().present()) {
            memoryImageAttacher.replace(memberId, memoryId, command.imageIds().value());
        }
        memoryRepository.flush();
        return memoryViewReader
                .read(memoryId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional
    public void delete(long memberId, long memoryId) {
        memoryRepository.delete(lockOwnMemory(memberId, memoryId));
    }

    @Transactional(readOnly = true)
    public void requireOwnMemory(long memberId, long memoryId) {
        loadOwnMemory(memberId, memoryId);
    }

    @Transactional
    public int deleteAllOf(long memberId) {
        return memoryMapper.deleteByMember(memberId);
    }

    private Memory lockOwnMemory(long memberId, long memoryId) {
        return requireOwn(memoryRepository.findByIdForUpdate(memoryId), memberId);
    }

    private Memory loadOwnMemory(long memberId, long memoryId) {
        return requireOwn(memoryRepository.findById(memoryId), memberId);
    }

    private static Memory requireOwn(Optional<Memory> found, long memberId) {
        Memory memory = found.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!memory.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return memory;
    }

    private void requireLinkable(long spotId) {
        if (!activeSpotChecker.isActive(spotId)) {
            throw MemoryWriteCommand.invalid("spotId", "연결할 수 없는 장소예요.");
        }
    }

    private static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, KoreaTime.ZONE);
    }
}
