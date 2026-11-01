package com.moewadeul.spot.infra.detail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpotDetailMapper {

    Optional<SpotDetailRow> selectActiveDetail(@Param("spotId") long spotId);

    Optional<SpotWeatherLocationRow> selectActiveWeatherLocation(@Param("spotId") long spotId);

    SpotRatingRow selectRating(@Param("spotId") long spotId);

    List<SpotRecentReviewRow> selectRecentReviews(@Param("spotId") long spotId, @Param("limit") int limit);

    List<SpotConfirmedStayRow> selectConfirmedStays(@Param("spotId") long spotId, @Param("today") LocalDate today);

    List<SpotReviewImageRow> selectReviewImages(@Param("reviewIds") List<Long> reviewIds);

    List<SpotConfirmedStayRow> selectSoloTrips(@Param("spotId") long spotId, @Param("today") LocalDate today);

    List<SpotConfirmedProgramRow> selectConfirmedPrograms(@Param("spotId") long spotId, @Param("from") Instant from);

    List<SpotRecruitingBasecampRow> selectRecruitingBasecamps(@Param("spotId") long spotId);
}
