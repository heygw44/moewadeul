package com.moewadeul.trust.infra.report;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportContentCleanupMapper {

    List<Long> selectHandledReportIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int clearHandledReportContent(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);

    List<Long> selectFinishedSanctionIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int clearFinishedSanctionReason(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);
}
