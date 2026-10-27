package com.moewadeul.community.application.post;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CommunityImageAttachmentChecker {

    static final String FIELD = "imageIds";
    static final String UNUSABLE_MESSAGE = "붙일 수 없는 이미지가 있어요.";

    private final CommunityImageRepository communityImageRepository;
    private final ImageStorage imageStorage;

    void verify(long memberId, Long postId, List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return;
        }
        requireValidShape(imageIds);
        Map<Long, CommunityImage> images = new HashMap<>();
        communityImageRepository.findAllById(imageIds).forEach(image -> images.put(image.getId(), image));
        for (Long imageId : imageIds) {
            CommunityImage image = images.get(imageId);
            requireAttachable(image, memberId, postId);
            if (!isAttachedTo(image, postId)) {
                requireUploaded(image);
            }
        }
    }

    private static void requireValidShape(List<Long> imageIds) {
        if (imageIds.size() > CommunityImage.MAX_PER_POST) {
            throw invalid("이미지는 " + CommunityImage.MAX_PER_POST + "장까지 붙일 수 있어요.");
        }
        if (new HashSet<>(imageIds).size() != imageIds.size()) {
            throw invalid("같은 이미지를 두 번 붙일 수 없어요.");
        }
        if (imageIds.stream().anyMatch(id -> id == null)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    private static void requireAttachable(CommunityImage image, long memberId, Long postId) {
        if (image == null || !image.isUploadedBy(memberId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
        if (image.isAttached() && !isAttachedTo(image, postId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    private static boolean isAttachedTo(CommunityImage image, Long postId) {
        return postId != null && image.isAttachedTo(postId);
    }

    private void requireUploaded(CommunityImage image) {
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
