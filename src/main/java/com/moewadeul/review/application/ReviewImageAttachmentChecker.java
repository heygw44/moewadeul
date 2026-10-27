package com.moewadeul.review.application;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ReviewImageAttachmentChecker {

    static final String FIELD = "imageIds";
    static final String UNUSABLE_MESSAGE = "붙일 수 없는 이미지가 있어요.";

    private final ReviewImageRepository reviewImageRepository;
    private final ImageStorage imageStorage;

    void verify(long memberId, Long reviewId, List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return;
        }
        requireValidShape(imageIds);
        Map<Long, ReviewImage> images = new HashMap<>();
        reviewImageRepository.findAllById(imageIds).forEach(image -> images.put(image.getId(), image));
        for (Long imageId : imageIds) {
            ReviewImage image = images.get(imageId);
            requireAttachable(image, memberId, reviewId);
            if (!isAttachedTo(image, reviewId)) {
                requireUploaded(image);
            }
        }
    }

    private static void requireValidShape(List<Long> imageIds) {
        if (imageIds.size() > ReviewImage.MAX_PER_REVIEW) {
            throw invalid("이미지는 " + ReviewImage.MAX_PER_REVIEW + "장까지 붙일 수 있어요.");
        }
        if (imageIds.stream().anyMatch(Objects::isNull)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
        if (new HashSet<>(imageIds).size() != imageIds.size()) {
            throw invalid("같은 이미지를 두 번 붙일 수 없어요.");
        }
    }

    private static void requireAttachable(ReviewImage image, long memberId, Long reviewId) {
        if (image == null || !image.isUploadedBy(memberId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
        if (image.isAttached() && !isAttachedTo(image, reviewId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    private static boolean isAttachedTo(ReviewImage image, Long reviewId) {
        return reviewId != null && image.isAttachedTo(reviewId);
    }

    private void requireUploaded(ReviewImage image) {
        boolean uploaded = imageStorage
                .findObjectSize(image.getObjectKey())
                .filter(size -> size == image.getSizeBytes())
                .isPresent();
        if (!uploaded) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    static InvalidFieldException invalid(String reason) {
        return new InvalidFieldException(FIELD, reason);
    }
}
