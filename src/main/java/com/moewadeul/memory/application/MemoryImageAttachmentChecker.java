package com.moewadeul.memory.application;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MemoryImageAttachmentChecker {

    static final String FIELD = "imageIds";
    static final String UNUSABLE_MESSAGE = "붙일 수 없는 사진이 있어요.";

    private final MemoryImageRepository memoryImageRepository;
    private final ImageStorage imageStorage;

    void verify(long memberId, Long memoryId, List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return;
        }
        requireValidShape(imageIds);
        Map<Long, MemoryImage> images = new HashMap<>();
        memoryImageRepository.findAllById(imageIds).forEach(image -> images.put(image.getId(), image));
        for (Long imageId : imageIds) {
            MemoryImage image = images.get(imageId);
            requireAttachable(image, memberId, memoryId);
            if (!isAttachedTo(image, memoryId)) {
                requireUploaded(image);
            }
        }
    }

    private static void requireValidShape(List<Long> imageIds) {
        if (imageIds.size() > MemoryImage.MAX_PER_MEMORY) {
            throw invalid("사진은 " + MemoryImage.MAX_PER_MEMORY + "장까지 붙일 수 있어요.");
        }
        if (imageIds.stream().anyMatch(Objects::isNull)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
        if (new HashSet<>(imageIds).size() != imageIds.size()) {
            throw invalid("같은 사진을 두 번 붙일 수 없어요.");
        }
    }

    private static void requireAttachable(MemoryImage image, long memberId, Long memoryId) {
        if (image == null || !image.isUploadedBy(memberId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
        if (image.isAttached() && !isAttachedTo(image, memoryId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    private static boolean isAttachedTo(MemoryImage image, Long memoryId) {
        return memoryId != null && image.isAttachedTo(memoryId);
    }

    private void requireUploaded(MemoryImage image) {
        boolean uploaded = isUploaded(image.getObjectKey(), image.getSizeBytes())
                && (!image.hasThumbnail() || isUploaded(image.getThumbnailObjectKey(), image.getThumbnailSizeBytes()));
        if (!uploaded) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    private boolean isUploaded(String objectKey, long sizeBytes) {
        return imageStorage
                .findObjectSize(objectKey)
                .filter(size -> size == sizeBytes)
                .isPresent();
    }

    static InvalidFieldException invalid(String reason) {
        return new InvalidFieldException(FIELD, reason);
    }
}
