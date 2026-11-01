package com.moewadeul.community.infra.report;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityReportContentCleanupMapper {

    int clearContentProcessedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
