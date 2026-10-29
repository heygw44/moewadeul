package com.moewadeul.community.infra.moderation;

import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminCommunityMapper {

    List<AdminCommunityPostRow> selectPosts(
            @Param("status") CommunityPostStatus status, @Param("offset") long offset, @Param("limit") int limit);

    List<AdminCommunityCommentRow> selectComments(
            @Param("status") CommunityCommentStatus status, @Param("offset") long offset, @Param("limit") int limit);

    List<ReportReasonCount> selectReasonCounts(
            @Param("targetType") CommunityReportTargetType targetType, @Param("targetIds") Collection<Long> targetIds);

    List<AdminCommunityRecentReportRow> selectRecentReports(
            @Param("targetType") CommunityReportTargetType targetType, @Param("targetIds") Collection<Long> targetIds);
}
