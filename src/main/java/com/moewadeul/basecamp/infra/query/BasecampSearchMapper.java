package com.moewadeul.basecamp.infra.query;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasecampSearchMapper {

    List<BasecampSearchRow> selectRecruiting(
            @Param("condition") BasecampSearchCondition condition,
            @Param("offset") long offset,
            @Param("limit") int limit);

    List<ViewerHistoryRow> selectViewerHistory(
            @Param("viewerId") long viewerId, @Param("basecampIds") List<Long> basecampIds);

    List<InProgressScheduleRow> selectInProgressSchedules(@Param("viewerId") long viewerId);

    SpotSummaryRow selectSpotSummary(@Param("spotId") long spotId);
}
