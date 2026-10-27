package com.moewadeul.trust.infra.report;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportContentCleanupMapper {

    int clearHandledReportContent(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int clearFinishedSanctionReason(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
