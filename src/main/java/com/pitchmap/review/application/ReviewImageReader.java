package com.pitchmap.review.application;

import com.pitchmap.common.storage.ImageStorage;
import com.pitchmap.review.infra.ReviewImageMapper;
import com.pitchmap.review.infra.ReviewImageRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 후기들에 붙은 이미지를 읽고 조회 URL을 붙인다. 호출하는 쪽의 트랜잭션 안에서 부른다.
 *
 * <p>한 페이지의 후기 이미지를 쿼리 한 번으로 읽는다. 조회 URL은 서버 안에서 서명을 계산하므로 저장소를 부르지 않는다.
 */
@Component
@RequiredArgsConstructor
class ReviewImageReader {

    private final ReviewImageMapper reviewImageMapper;
    private final ImageStorage imageStorage;

    /** 호출하면 reviewIds인 후기마다 붙은 이미지를 후기 안 순서대로 돌려준다. 이미지가 없는 후기는 결과에 없다. */
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
