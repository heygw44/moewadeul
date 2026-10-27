package com.moewadeul.review.application;

import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
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
class ReviewImageAttacher {

    private final ReviewImageRepository reviewImageRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(long memberId, long reviewId, List<Long> imageIds) {
        List<Long> currentIds = reviewImageRepository.findIdsByReviewId(reviewId);
        if (currentIds.isEmpty() && imageIds.isEmpty()) {
            return;
        }
        Set<Long> lockIds = new LinkedHashSet<>(imageIds);
        lockIds.addAll(currentIds);
        Map<Long, ReviewImage> locked = lock(lockIds);
        List<ReviewImage> next = new ArrayList<>();
        for (Long imageId : imageIds) {
            next.add(requireAttachable(locked.get(imageId), memberId, reviewId));
        }
        for (Long currentId : currentIds) {
            if (!imageIds.contains(currentId)) {
                locked.get(currentId).detach();
            }
        }
        for (int order = 0; order < next.size(); order++) {
            next.get(order).attachTo(reviewId, order);
        }
    }

    private Map<Long, ReviewImage> lock(Collection<Long> ids) {
        Map<Long, ReviewImage> locked = new HashMap<>();
        reviewImageRepository.findAllByIdForUpdate(ids).forEach(image -> locked.put(image.getId(), image));
        return locked;
    }

    private static ReviewImage requireAttachable(ReviewImage image, long memberId, long reviewId) {
        boolean attachable =
                image != null && image.isUploadedBy(memberId) && (!image.isAttached() || image.isAttachedTo(reviewId));
        if (!attachable) {
            throw ReviewImageAttachmentChecker.invalid(ReviewImageAttachmentChecker.UNUSABLE_MESSAGE);
        }
        return image;
    }
}
