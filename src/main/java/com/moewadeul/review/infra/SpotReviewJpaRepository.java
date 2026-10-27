package com.moewadeul.review.infra;

import com.moewadeul.review.domain.SpotReview;
import com.moewadeul.review.domain.SpotReviewRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotReviewJpaRepository extends JpaRepository<SpotReview, Long>, SpotReviewRepository {

    @Override
    SpotReview saveAndFlush(SpotReview review);

    @Override
    Optional<SpotReview> findById(Long id);

    @Override
    void delete(SpotReview review);

    @Override
    void flush();
}
