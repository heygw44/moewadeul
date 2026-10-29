package com.moewadeul.memory.infra.moderation;

import com.moewadeul.common.moderation.RecentReportRow;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.memory.domain.MemoryStatus;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMemoryMapper {

    List<AdminMemoryRow> selectMemories(
            @Param("status") MemoryStatus status, @Param("offset") long offset, @Param("limit") int limit);

    default List<ReportReasonCount> selectReasonCounts(Collection<Long> memoryIds) {
        return memoryIds.isEmpty() ? List.of() : selectReasonCountsOfNonEmpty(memoryIds);
    }

    List<ReportReasonCount> selectReasonCountsOfNonEmpty(@Param("targetIds") Collection<Long> memoryIds);

    default List<RecentReportRow> selectRecentReports(Collection<Long> memoryIds) {
        return memoryIds.isEmpty() ? List.of() : selectRecentReportsOfNonEmpty(memoryIds);
    }

    List<RecentReportRow> selectRecentReportsOfNonEmpty(@Param("targetIds") Collection<Long> memoryIds);
}
