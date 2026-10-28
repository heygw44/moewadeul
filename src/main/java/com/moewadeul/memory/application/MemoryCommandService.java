package com.moewadeul.memory.application;

import com.moewadeul.common.web.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemoryCommandService {

    private final MemoryWriter memoryWriter;
    private final MemoryImageAttachmentPrecheck memoryImageAttachmentPrecheck;

    public long write(long memberId, MemoryWriteCommand command) {
        memoryImageAttachmentPrecheck.verify(memberId, null, command.imageIds());
        return memoryWriter.write(memberId, command);
    }

    public MemoryView revise(long memberId, long memoryId, MemoryReviseCommand command) {
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            memoryWriter.requireOwnMemory(memberId, memoryId);
            if (imageIds.value() == null) {
                throw MemoryImageAttachmentPrecheck.invalid("imageIds는 비울 수 없어요. 사진을 모두 떼려면 빈 배열을 보내 주세요.");
            }
            memoryImageAttachmentPrecheck.verify(memberId, memoryId, imageIds.value());
        }
        return memoryWriter.revise(memberId, memoryId, command);
    }

    public void delete(long memberId, long memoryId) {
        memoryWriter.delete(memberId, memoryId);
    }
}
