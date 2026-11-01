package com.moewadeul.trust.infra.companion;

import com.moewadeul.trust.domain.companion.CompanionReview;
import com.moewadeul.trust.domain.companion.CompanionReviewRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionReviewJpaRepository extends JpaRepository<CompanionReview, Long>, CompanionReviewRepository {

    @Override
    CompanionReview saveAndFlush(CompanionReview review);

    @Override
    Optional<CompanionReview> findById(Long id);
}
