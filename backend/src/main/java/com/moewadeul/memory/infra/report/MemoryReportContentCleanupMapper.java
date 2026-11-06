package com.moewadeul.memory.infra.report;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryReportContentCleanupMapper {

    List<Long> selectIdsProcessedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int clearContentByIds(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);
}
