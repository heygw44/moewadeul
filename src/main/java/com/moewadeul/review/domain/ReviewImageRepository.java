package com.moewadeul.review.domain;

import java.util.Collection;
import java.util.List;

public interface ReviewImageRepository {

    ReviewImage saveAndFlush(ReviewImage image);

    List<ReviewImage> findAllById(Iterable<Long> ids);

    List<ReviewImage> findAllByIdForUpdate(Collection<Long> ids);

    List<Long> findIdsByReviewId(long reviewId);
}
