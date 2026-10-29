package com.moewadeul.spot.infra.moderation;

import com.moewadeul.common.moderation.ReportReasonCount;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminSpotMapper {

    List<AdminSpotRow> selectPage(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    List<ReportReasonCount> selectReasonCounts(@Param("spotIds") Collection<Long> spotIds);

    List<AdminSpotRecentReportRow> selectRecentReports(@Param("spotIds") Collection<Long> spotIds);
}
