package com.moewadeul.memory.application;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.image.application.ImageAttachments;
import com.moewadeul.common.patch.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemoryCommandService {

    private final MemoryWriter memoryWriter;
    private final MemoryImageAttachments memoryImageAttachments;

    public long write(long memberId, MemoryWriteCommand command) {
        memoryImageAttachments.precheck(memberId, null, command.imageIds());
        return memoryWriter.write(memberId, command);
    }

    public MemoryView revise(long memberId, long memoryId, MemoryReviseCommand command) {
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            memoryWriter.requireOwnMemory(memberId, memoryId);
            if (imageIds.value() == null) {
                throw new InvalidFieldException(ImageAttachments.FIELD, "imageIds는 비울 수 없어요. 사진을 모두 떼려면 빈 배열을 보내 주세요.");
            }
            memoryImageAttachments.precheck(memberId, memoryId, imageIds.value());
        }
        return memoryWriter.revise(memberId, memoryId, command);
    }

    public void delete(long memberId, long memoryId) {
        memoryWriter.delete(memberId, memoryId);
    }
}
