package com.moewadeul.review.domain;

import java.util.Collection;
import java.util.List;

public interface ReviewImageRepository {

    ReviewImage saveAndFlush(ReviewImage image);

    /** 호출하면 ids인 이미지를 잠그지 않고 읽는다. 없는 ID는 결과에 없다. */
    List<ReviewImage> findAllById(Iterable<Long> ids);

    /** 호출하면 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 트랜잭션 안에서만 부른다. 없는 ID는 결과에 없다. */
    List<ReviewImage> findAllByIdForUpdate(Collection<Long> ids);

    /** 호출하면 reviewId인 후기에 붙은 이미지의 ID만 후기 안 순서대로 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않는다. */
    List<Long> findIdsByReviewId(long reviewId);
}
