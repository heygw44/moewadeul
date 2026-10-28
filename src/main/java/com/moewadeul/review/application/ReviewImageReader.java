package com.moewadeul.review.application;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.review.infra.ReviewImageMapper;
import com.moewadeul.review.infra.ReviewImageRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ReviewImageReader {

    private final ReviewImageMapper reviewImageMapper;
    private final ImageStorage imageStorage;

    Map<Long, List<SpotReviewImage>> readByReviewIds(Collection<Long> reviewIds) {
        Map<Long, List<SpotReviewImage>> imagesByReview = new HashMap<>();
        if (reviewIds.isEmpty()) {
            return imagesByReview;
        }
        for (ReviewImageRow row : reviewImageMapper.selectByReviewIds(reviewIds)) {
            imagesByReview
                    .computeIfAbsent(row.reviewId(), id -> new ArrayList<>())
                    .add(new SpotReviewImage(row.imageId(), imageStorage.presignView(row.objectKey())));
        }
        return imagesByReview;
    }
}
