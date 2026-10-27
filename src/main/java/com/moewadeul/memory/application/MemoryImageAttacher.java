package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class MemoryImageAttacher {

    private final MemoryImageRepository memoryImageRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(long memberId, long memoryId, List<Long> imageIds) {
        List<Long> currentIds = memoryImageRepository.findIdsByMemoryId(memoryId);
        if (currentIds.isEmpty() && imageIds.isEmpty()) {
            return;
        }
        Set<Long> lockIds = new LinkedHashSet<>(imageIds);
        lockIds.addAll(currentIds);
        Map<Long, MemoryImage> locked = lock(lockIds);
        List<MemoryImage> next = new ArrayList<>();
        for (Long imageId : imageIds) {
            next.add(requireAttachable(locked.get(imageId), memberId, memoryId));
        }
        for (Long currentId : currentIds) {
            if (!imageIds.contains(currentId)) {
                locked.get(currentId).detach();
            }
        }
        for (int order = 0; order < next.size(); order++) {
            next.get(order).attachTo(memoryId, order);
        }
    }

    private Map<Long, MemoryImage> lock(Collection<Long> ids) {
        Map<Long, MemoryImage> locked = new HashMap<>();
        memoryImageRepository.findAllByIdForUpdate(ids).forEach(image -> locked.put(image.getId(), image));
        return locked;
    }

    private static MemoryImage requireAttachable(MemoryImage image, long memberId, long memoryId) {
        boolean attachable =
                image != null && image.isUploadedBy(memberId) && (!image.isAttached() || image.isAttachedTo(memoryId));
        if (!attachable) {
            throw MemoryImageAttachmentPrecheck.invalid(MemoryImageAttachmentPrecheck.UNUSABLE_MESSAGE_FOR_ANY_REASON);
        }
        return image;
    }
}
