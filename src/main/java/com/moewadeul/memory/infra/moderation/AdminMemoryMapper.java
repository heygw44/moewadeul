package com.moewadeul.memory.infra.moderation;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMemoryMapper {

    List<AdminMemoryRow> selectMemories(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    default List<AdminMemoryRecentReportRow> selectRecentReports(Collection<Long> memoryIds) {
        return memoryIds.isEmpty() ? List.of() : selectRecentReportsOfNonEmpty(memoryIds);
    }

    List<AdminMemoryRecentReportRow> selectRecentReportsOfNonEmpty(@Param("memoryIds") Collection<Long> memoryIds);
}
