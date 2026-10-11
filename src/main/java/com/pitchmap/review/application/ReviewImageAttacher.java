package com.pitchmap.review.application;

import com.pitchmap.review.domain.ReviewImage;
import com.pitchmap.review.domain.ReviewImageRepository;
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
 * 후기에 붙는 이미지 목록을 바꾼다. 후기를 저장하는 트랜잭션 안에서만 부른다.
 *
 * <p>같은 이미지를 동시에 두 후기에 붙이는 요청이 모두 통과하지 않도록 이미지 행을 쓰기 잠금으로 읽고, 잠금을 쥔 채 소유자와 붙은 상태를 다시
 * 검사한다. 늦게 온 요청은 잠금이 풀리기를 기다린 뒤 이미 붙은 상태를 보고 거부된다. 거부되면 예외가 트랜잭션 전체를 되돌려서 후기도 저장되지 않는다.
 */
@Component
@RequiredArgsConstructor
class ReviewImageAttacher {

    private final ReviewImageRepository reviewImageRepository;

    /**
     * 호출하면 reviewId인 후기의 이미지를 imageIds 순서대로 통째로 바꾼다. 목록에서 빠진 이미지는 후기에서 뗀다. 빈 목록이면 붙은 이미지를 모두 뗀다.
     *
     * <p>이미지가 없거나, 다른 회원이 올렸거나, 다른 후기에 붙어 있으면 {@code imageIds} 필드의 INVALID_INPUT으로 거부한다.
     */
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
