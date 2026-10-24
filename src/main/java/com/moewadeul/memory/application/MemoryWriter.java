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

/**
 * 기록을 저장하고, 고치고, 지우는 트랜잭션을 가진다. {@link MemoryCommandService}가 사진을 저장소까지 확인한 뒤에 부른다.
 *
 * <p>기록에 연결할 수 있는 장소는 지도에 보이는(ACTIVE) 장소뿐이다. 장소가 없거나 ACTIVE가 아니면 {@code spotId} 필드의 INVALID_INPUT으로
 * 거부한다. 고칠 때는 장소를 바꿀 때만 다시 확인한다. 그래서 장소가 숨겨진 뒤에도 작성자는 장소를 그대로 둔 채 기록을 고치거나 지울 수 있다.
 *
 * <p>고치거나 지울 때 검사 순서는 기록 확인, 작성자 확인이다. 기록이 없으면 NOT_FOUND, 남의 기록이면 비공개 기록이어도 ACCESS_DENIED다.
 */
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
        // imageIds를 보냈으면 MemoryCommandService가 null을 이미 거부했으므로 값이 있다.
        if (command.imageIds().present()) {
            memoryImageAttacher.replace(memberId, memoryId, command.imageIds().value());
        }
        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.
        memoryRepository.flush();
        return memoryViewReader
                .read(memoryId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 기록 행을 지우면 FK가 붙은 사진의 기록 ID를 NULL로 바꾸므로, 사진은 붙지 않은 업로드가 되어 정리 작업이 지운다.
    @Transactional
    public void delete(long memberId, long memoryId) {
        memoryRepository.delete(lockOwnMemory(memberId, memoryId));
    }

    /** 호출하면 memoryId인 기록이 있고 memberId가 쓴 기록인지 확인한다. 없으면 NOT_FOUND로, 남의 기록이면 ACCESS_DENIED로 거부한다. */
    @Transactional(readOnly = true)
    public void requireOwnMemory(long memberId, long memoryId) {
        loadOwnMemory(memberId, memoryId);
    }

    /** 호출하면 memberId인 회원의 기록을 모두 지우고 지운 수를 돌려준다. 붙어 있던 사진은 붙지 않은 상태가 된다. */
    @Transactional
    public int deleteAllOf(long memberId) {
        return memoryMapper.deleteByMember(memberId);
    }

    // 숨김·신고 처리와 같은 기록 행 잠금을 첫 쿼리로 잡는다. 잠그지 않고 읽으면 그사이 커밋된 HIDDEN·검토 대기 상태를 이 트랜잭션이
    // 행 전체를 쓰면서 ACTIVE로 되돌린다. 같은 기록의 사진을 바꾸는 요청끼리도 이 잠금으로 줄을 서므로, 늦은 요청은 먼저 붙은 사진을 보고 떼어 낸다.
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

    // 방문일은 한국 날짜로 받으므로, 오늘도 한국 날짜로 계산해서 비교한다. 오늘 다녀온 곳의 기록은 쓸 수 있다.
    private static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, KoreaTime.ZONE);
    }
}
