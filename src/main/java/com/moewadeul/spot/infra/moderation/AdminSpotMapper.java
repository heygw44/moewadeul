package com.moewadeul.spot.infra.moderation;

import com.moewadeul.common.moderation.RecentReportRow;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.spot.domain.SpotStatus;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminSpotMapper {

    List<AdminSpotRow> selectPage(
            @Param("status") SpotStatus status, @Param("offset") long offset, @Param("limit") int limit);

    List<ReportReasonCount> selectReasonCounts(@Param("targetIds") Collection<Long> targetIds);

    List<RecentReportRow> selectRecentReports(@Param("targetIds") Collection<Long> targetIds);
}
