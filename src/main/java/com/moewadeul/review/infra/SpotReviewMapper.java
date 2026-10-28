package com.moewadeul.review.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpotReviewMapper {

    List<SpotReviewRow> selectBySpot(
            @Param("spotId") long spotId, @Param("offset") long offset, @Param("limit") int limit);

    Optional<SpotReviewRow> selectById(@Param("reviewId") long reviewId);
}
