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

/**
 * 기록에 붙는 사진 목록을 바꾼다. 기록을 저장하는 트랜잭션 안에서만 부른다.
 *
 * <p>같은 사진을 동시에 두 기록에 붙이는 요청이 모두 통과하지 않도록 사진 행을 쓰기 잠금으로 읽고, 잠금을 쥔 채 소유자와 붙은 상태를 다시
 * 검사한다. 늦게 온 요청은 잠금이 풀리기를 기다린 뒤 이미 붙은 상태를 보고 거부된다. 거부되면 예외가 트랜잭션 전체를 되돌려서 기록도 저장되지 않는다.
 */
@Component
@RequiredArgsConstructor
class MemoryImageAttacher {

    private final MemoryImageRepository memoryImageRepository;

    /**
     * 호출하면 memoryId인 기록의 사진을 imageIds 순서대로 통째로 바꾼다. 목록에서 빠진 사진은 기록에서 뗀다. 빈 목록이면 붙은 사진을 모두 뗀다.
     *
     * <p>사진이 없거나, 다른 회원이 올렸거나, 다른 기록에 붙어 있으면 {@code imageIds} 필드의 INVALID_INPUT으로 거부한다.
     */
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
            throw MemoryImageAttachmentChecker.invalid(MemoryImageAttachmentChecker.UNUSABLE_MESSAGE);
        }
        return image;
    }
}
