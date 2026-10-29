package com.moewadeul.community.application.moderation;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.moderation.ReportReasonCount;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import com.moewadeul.community.infra.moderation.AdminCommunityCommentRow;
import com.moewadeul.community.infra.moderation.AdminCommunityMapper;
import com.moewadeul.community.infra.moderation.AdminCommunityPostRow;
import com.moewadeul.community.infra.moderation.AdminCommunityRecentReportRow;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCommunityQueryService {

    private static final Set<CommunityPostStatus> POST_REVIEWABLE_STATUSES =
            EnumSet.of(CommunityPostStatus.PENDING_REVIEW, CommunityPostStatus.HIDDEN);
    private static final Set<CommunityCommentStatus> COMMENT_REVIEWABLE_STATUSES =
            EnumSet.of(CommunityCommentStatus.PENDING_REVIEW, CommunityCommentStatus.HIDDEN);
    private static final String STATUS_MESSAGE = "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.";

    private final AdminCommunityMapper adminCommunityMapper;

    @Transactional(readOnly = true)
    public PageSlice<AdminCommunityPostSummary> listPosts(String status, int page, int size) {
        CommunityPostStatus postStatus =
                parseStatus(status, CommunityPostStatus.PENDING_REVIEW, POST_REVIEWABLE_STATUSES);
        List<AdminCommunityPostRow> rows =
                adminCommunityMapper.selectPosts(postStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminCommunityPostRow> slice = PageSlice.of(rows, page, size);
        List<Long> postIds =
                slice.content().stream().map(AdminCommunityPostRow::postId).toList();
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(CommunityReportTargetType.POST, postIds);
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
        CommunityCommentStatus commentStatus =
                parseStatus(status, CommunityCommentStatus.PENDING_REVIEW, COMMENT_REVIEWABLE_STATUSES);
        List<AdminCommunityCommentRow> rows = adminCommunityMapper.selectComments(
                commentStatus, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminCommunityCommentRow> slice = PageSlice.of(rows, page, size);
        List<Long> commentIds = slice.content().stream()
                .map(AdminCommunityCommentRow::commentId)
                .toList();
        Map<Long, List<AdminCommunityRecentReport>> recent =
                readRecentReports(CommunityReportTargetType.COMMENT, commentIds);
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

    private Map<Long, List<AdminCommunityRecentReport>> readRecentReports(
            CommunityReportTargetType targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return adminCommunityMapper.selectRecentReports(targetType, targetIds).stream()
                .collect(Collectors.groupingBy(
                        AdminCommunityRecentReportRow::targetId,
                        Collectors.mapping(
                                row -> new AdminCommunityRecentReport(row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static <E extends Enum<E>> E parseStatus(String status, E defaultStatus, Set<E> reviewable) {
        if (status == null) {
            return defaultStatus;
        }
        return EnumParser.parseAmong(reviewable, status, STATUS_MESSAGE);
    }
}
