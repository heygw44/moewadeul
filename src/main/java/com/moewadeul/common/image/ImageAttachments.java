package com.moewadeul.common.image;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.transaction.InsideTransaction;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class ImageAttachments<T extends AttachableImage> {

    public static final String FIELD = "imageIds";

    private final ImageKind kind;
    private final AttachableImageRepository<T> imageRepository;
    private final ImageStorage imageStorage;

    protected ImageAttachments(
            ImageKind kind, AttachableImageRepository<T> imageRepository, ImageStorage imageStorage) {
        this.kind = kind;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
    }

    public void precheck(long memberId, Long parentId, List<Long> imageIds) {
        OutsideTransaction.require("이미지가 저장소에 있는지 묻는 동안 DB 커넥션을 쥐면 안 된다.");
        if (imageIds.isEmpty()) {
            return;
        }
        requireValidShape(imageIds);
        Map<Long, T> images = new HashMap<>();
        imageRepository.findAllById(imageIds).forEach(image -> images.put(image.getId(), image));
        for (Long imageId : imageIds) {
            T image = requireAttachable(images.get(imageId), memberId, parentId);
            if (!isAttachedTo(image, parentId)) {
                requireUploaded(image);
            }
        }
    }

    public boolean replace(long memberId, long parentId, List<Long> imageIds) {
        InsideTransaction.require("이미지 행을 잠근 채 대상과 같은 트랜잭션에서 붙여야 한다.");
        List<Long> currentIds = imageRepository.findIdsByParentId(parentId);
        if (currentIds.isEmpty() && imageIds.isEmpty()) {
            return false;
        }
        Set<Long> lockIds = new LinkedHashSet<>(imageIds);
        lockIds.addAll(currentIds);
        Map<Long, T> locked = lock(lockIds);
        List<T> next = new ArrayList<>();
        for (Long imageId : imageIds) {
            next.add(requireAttachable(locked.get(imageId), memberId, parentId));
        }
        for (Long currentId : currentIds) {
            if (!imageIds.contains(currentId)) {
                locked.get(currentId).detach();
            }
        }
        for (int order = 0; order < next.size(); order++) {
            next.get(order).attachTo(parentId, order);
        }
        return !currentIds.equals(imageIds);
    }

    private void requireValidShape(List<Long> imageIds) {
        ImageMessages messages = kind.messages();
        if (imageIds.size() > kind.maxPerParent()) {
            throw invalid(messages.tooMany(kind.maxPerParent()));
        }
        if (imageIds.stream().anyMatch(Objects::isNull)) {
            throw invalid(messages.unusable());
        }
        if (new HashSet<>(imageIds).size() != imageIds.size()) {
            throw invalid(messages.duplicated());
        }
    }

    private T requireAttachable(T image, long memberId, Long parentId) {
        boolean attachable =
                image != null && image.isUploadedBy(memberId) && (!image.isAttached() || isAttachedTo(image, parentId));
        if (!attachable) {
            throw unusable();
        }
        return image;
    }

    private static boolean isAttachedTo(AttachableImage image, Long parentId) {
        return parentId != null && image.isAttachedTo(parentId);
    }

    private void requireUploaded(T image) {
        boolean uploaded = isUploaded(image.getObjectKey(), image.getSizeBytes())
                && image.thumbnail()
                        .map(thumbnail -> isUploaded(thumbnail.objectKey(), thumbnail.sizeBytes()))
                        .orElse(true);
        if (!uploaded) {
            throw unusable();
        }
    }

    private boolean isUploaded(String objectKey, long sizeBytes) {
        return imageStorage
                .findObjectSize(objectKey)
                .filter(size -> size == sizeBytes)
                .isPresent();
    }

    private Map<Long, T> lock(Collection<Long> ids) {
        Map<Long, T> locked = new HashMap<>();
        imageRepository.findAllByIdForUpdate(ids).forEach(image -> locked.put(image.getId(), image));
        return locked;
    }

    private InvalidFieldException unusable() {
        return invalid(kind.messages().unusable());
    }

    private static InvalidFieldException invalid(String reason) {
        return new InvalidFieldException(FIELD, reason);
    }
}
