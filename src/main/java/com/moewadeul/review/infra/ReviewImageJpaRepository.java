package com.moewadeul.review.infra;

import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewImageJpaRepository extends JpaRepository<ReviewImage, Long>, ReviewImageRepository {

    @Override
    ReviewImage saveAndFlush(ReviewImage image);

    @Override
    List<ReviewImage> findAllById(Iterable<Long> ids);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ReviewImage i WHERE i.id IN :ids ORDER BY i.id")
    List<ReviewImage> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Override
    @Query("SELECT i.id FROM ReviewImage i WHERE i.reviewId = :reviewId ORDER BY i.displayOrder")
    List<Long> findIdsByReviewId(@Param("reviewId") long reviewId);
}
