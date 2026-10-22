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

/**
 * 기록에 붙이려는 사진이 붙일 수 있는 사진인지 저장소까지 확인한다. 이 클래스는 트랜잭션을 열지 않는다.
 *
 * <p>저장소에 객체가 있는지 묻는 호출은 느릴 수 있어서, 기록을 저장하는 트랜잭션이 DB 커넥션과 행 잠금을 쥔 채로 기다리면 안 된다. 그래서
 * 기록을 저장하기 전에 트랜잭션 밖에서 확인한다. 하지만 확인한 뒤 저장하기 전에 다른 요청이 같은 사진을 붙일 수 있으므로, 트랜잭션 안에서
 * {@link MemoryImageAttacher}가 행을 잠그고 같은 조건을 다시 검사한다. 여기서 한 검사는 빨리 거부하기 위한 것이다.
 */
@Component
@RequiredArgsConstructor
class MemoryImageAttachmentChecker {

    static final String FIELD = "imageIds";
    static final String UNUSABLE_MESSAGE = "붙일 수 없는 사진이 있어요.";

    private final MemoryImageRepository memoryImageRepository;
    private final ImageStorage imageStorage;

    /**
     * 호출하면 imageIds를 memberId인 회원이 memoryId인 기록(새 기록이면 null)에 붙일 수 있는지 확인한다.
     *
     * <p>10장을 넘거나 같은 ID가 겹치거나, 없는 사진이거나 다른 회원이 올린 사진이거나 다른 기록에 이미 붙었거나, 저장소에 객체가 없거나
     * 올린 크기가 발급할 때 서명한 크기와 다르면 {@code imageIds} 필드의 INVALID_INPUT으로 거부한다. 이미 이 기록에 붙은 사진은
     * 저장소를 다시 확인하지 않는다.
     */
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

    // 없는 사진과 남의 사진을 같은 메시지로 거부해서, 다른 회원의 사진 ID가 존재하는지 알 수 없게 한다.
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

    // 썸네일 URL도 함께 발급한 사진은 썸네일까지 올라와야 붙일 수 있다. 썸네일만 빠져도 원본이 없을 때와 같은 메시지로 거부한다.
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
