package com.moewadeul.spot.infra.bakji;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BakjiReportContentCleanupMapper {

    int clearContentProcessedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
