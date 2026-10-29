package com.moewadeul.community.infra.moderation;

import com.moewadeul.common.moderation.ReportReasonCount;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminCommunityMapper {

    List<AdminCommunityPostRow> selectPosts(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    List<AdminCommunityCommentRow> selectComments(
            @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);

    List<ReportReasonCount> selectReasonCounts(
            @Param("targetType") String targetType, @Param("targetIds") Collection<Long> targetIds);

    List<AdminCommunityRecentReportRow> selectRecentReports(
            @Param("targetType") String targetType, @Param("targetIds") Collection<Long> targetIds);
}
