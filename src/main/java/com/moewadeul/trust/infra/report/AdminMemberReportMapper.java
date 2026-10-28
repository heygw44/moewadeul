package com.moewadeul.trust.infra.report;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMemberReportMapper {

    List<AdminMemberReportRow> selectList(
            @Param("status") String status,
            @Param("urgent") Boolean urgent,
            @Param("offset") long offset,
            @Param("limit") int limit);

    Optional<AdminMemberReportDetailRow> selectDetail(@Param("reportId") long reportId);

    List<String> selectReviewTags(@Param("reviewId") long reviewId);

    List<AdminSanctionHistoryRow> selectSanctionHistory(@Param("memberId") long memberId);
}
