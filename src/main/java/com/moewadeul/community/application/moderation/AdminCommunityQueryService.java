package com.moewadeul.community.application.moderation;

import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.moderation.ModerationStatus;
import com.moewadeul.common.moderation.RecentReport;
import com.moewadeul.common.moderation.RecentReportRow;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import com.moewadeul.community.infra.moderation.AdminCommunityCommentRow;
import com.moewadeul.community.infra.moderation.AdminCommunityMapper;
import com.moewadeul.community.infra.moderation.AdminCommunityPostRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCommunityQueryService {

    private final AdminCommunityMapper adminCommunityMapper;

    @Transactional(readOnly = true)
    public PageSlice<AdminCommunityPostSummary> listPosts(String status, int page, int size) {
        CommunityPostStatus postStatus = ModerationStatus.parseReviewable(CommunityPostStatus.class, status);
        List<AdminCommunityPostRow> rows =
                adminCommunityMapper.selectPosts(postStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminCommunityPostRow> slice = PageSlice.of(rows, page, size);
        List<Long> postIds =
                slice.content().stream().map(AdminCommunityPostRow::postId).toList();
        Map<Long, List<RecentReport>> recent = readRecentReports(CommunityReportTargetType.POST, postIds);
        Map<Long, Map<String, Long>> reasonCounts = readReasonCounts(CommunityReportTargetType.POST, postIds);
        return slice.map(row -> new AdminCommunityPostSummary(
                row.postId(),
                row.title(),
                row.content(),
                new AdminCommunityAuthor(row.authorId(), row.authorNickname()),
                row.status().name(),
                row.reportCount(),
                reasonCounts.get(row.postId()),
                recent.getOrDefault(row.postId(), List.of()),
                row.statusChangedAt()));
    }

    @Transactional(readOnly = true)
    public PageSlice<AdminCommunityCommentSummary> listComments(String status, int page, int size) {
        CommunityCommentStatus commentStatus = ModerationStatus.parseReviewable(CommunityCommentStatus.class, status);
        List<AdminCommunityCommentRow> rows = adminCommunityMapper.selectComments(
                commentStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminCommunityCommentRow> slice = PageSlice.of(rows, page, size);
        List<Long> commentIds = slice.content().stream()
                .map(AdminCommunityCommentRow::commentId)
                .toList();
        Map<Long, List<RecentReport>> recent = readRecentReports(CommunityReportTargetType.COMMENT, commentIds);
        Map<Long, Map<String, Long>> reasonCounts = readReasonCounts(CommunityReportTargetType.COMMENT, commentIds);
        return slice.map(row -> new AdminCommunityCommentSummary(
                row.commentId(),
                row.postId(),
                row.content(),
                new AdminCommunityAuthor(row.authorId(), row.authorNickname()),
                row.status().name(),
                row.reportCount(),
                reasonCounts.get(row.commentId()),
                recent.getOrDefault(row.commentId(), List.of()),
                row.statusChangedAt()));
    }

    private Map<Long, Map<String, Long>> readReasonCounts(CommunityReportTargetType targetType, List<Long> targetIds) {
        List<ReportReasonCount> counts =
                targetIds.isEmpty() ? List.of() : adminCommunityMapper.selectReasonCounts(targetType, targetIds);
        return ReportReasonCount.byTarget(ContentReportReason.class, targetIds, counts);
    }

    private Map<Long, List<RecentReport>> readRecentReports(
            CommunityReportTargetType targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return RecentReportRow.byTarget(adminCommunityMapper.selectRecentReports(targetType, targetIds));
    }
}
