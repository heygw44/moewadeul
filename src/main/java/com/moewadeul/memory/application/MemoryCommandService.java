package com.moewadeul.memory.application;

import com.moewadeul.common.web.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원이 추억 기록을 쓰고, 자기 기록을 고치거나 지운다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 기록에 사진을 붙일 때 저장소에 객체가 있는지 묻는 호출은 DB 트랜잭션 밖에서 해야 하기 때문이다. 사진을
 * 확인한 뒤에 트랜잭션을 가진 {@link MemoryWriter}를 불러 기록을 저장한다. 기록 내용의 규칙과 작성자 검사는 그쪽에 있다.
 */
@Service
@RequiredArgsConstructor
public class MemoryCommandService {

    private final MemoryWriter memoryWriter;
    private final MemoryImageAttachmentChecker memoryImageAttachmentChecker;

    /**
     * 호출하면 memberId인 회원의 기록을 저장하고 기록 ID를 돌려준다. 공개 범위를 보내지 않으면 비공개로 저장한다.
     *
     * <p>좌표, 방문일, 박 수, 글, 공개 범위가 규칙을 어기거나 연결할 장소가 ACTIVE가 아니면 그 필드의 INVALID_INPUT으로 거부한다. 붙일 사진이
     * 10장을 넘거나 겹치거나, 회원이 올리지 않았거나, 이미 다른 기록에 붙었거나, 저장소에 없거나 크기가 다르면 {@code imageIds} 필드의
     * INVALID_INPUT으로 거부하고 기록도 저장하지 않는다.
     */
    public long write(long memberId, MemoryWriteCommand command) {
        memoryImageAttachmentChecker.verify(memberId, null, command.imageIds());
        return memoryWriter.write(memberId, command);
    }

    /**
     * 호출하면 작성자 memberId가 memoryId인 기록에서 요청에 담은 필드만 고치고, 고친 뒤의 기록을 돌려준다. imageIds를 보내면 사진을 그 순서대로
     * 통째로 바꾸고, 이미 이 기록에 붙은 사진은 다시 보내도 된다.
     *
     * <p>기록이 없으면 NOT_FOUND로, 다른 회원의 기록이면 ACCESS_DENIED로 거부한다. 값이 규칙을 어기면 그 필드의 INVALID_INPUT으로 거부한다.
     */
    public MemoryView revise(long memberId, long memoryId, MemoryReviseCommand command) {
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            // 기록이 없거나 남의 기록이면 사진 검사보다 먼저 404나 403으로 거부한다.
            memoryWriter.requireOwnMemory(memberId, memoryId);
            if (imageIds.value() == null) {
                throw MemoryImageAttachmentChecker.invalid("imageIds는 비울 수 없어요. 사진을 모두 떼려면 빈 배열을 보내 주세요.");
            }
            memoryImageAttachmentChecker.verify(memberId, memoryId, imageIds.value());
        }
        return memoryWriter.revise(memberId, memoryId, command);
    }

    /**
     * 호출하면 작성자 memberId의 기록을 지운다. 붙은 사진은 기록에서 떨어지고 정리 작업이 지운다. 기록이 없으면 NOT_FOUND로, 다른 회원의
     * 기록이면 ACCESS_DENIED로 거부한다.
     */
    public void delete(long memberId, long memoryId) {
        memoryWriter.delete(memberId, memoryId);
    }
}
