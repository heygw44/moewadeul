package com.moewadeul.trust.domain.companion;

import java.util.Optional;

public interface CompanionReviewRepository {

    CompanionReview saveAndFlush(CompanionReview review);

    Optional<CompanionReview> findById(Long id);
}
