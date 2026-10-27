package com.moewadeul.trust.infra.report;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberReportMapper {

    Optional<MemberReportContextRow> selectContext(
            @Param("basecampId") long basecampId,
            @Param("reporterId") long reporterId,
            @Param("targetId") long targetId,
            @Param("kind") String kind,
            @Param("companionReviewId") Long companionReviewId);
}
