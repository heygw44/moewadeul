package com.moewadeul.review.domain;

import java.util.Optional;

public interface SpotReviewRepository {

    SpotReview saveAndFlush(SpotReview review);

    Optional<SpotReview> findById(Long id);

    void delete(SpotReview review);

    void flush();
}
