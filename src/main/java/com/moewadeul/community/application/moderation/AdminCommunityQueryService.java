package com.moewadeul.community.application.moderation;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.paging.PageSlice;
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

    private static final Set<CommunityPostStatus> REVIEWABLE_STATUSES =
            EnumSet.of(CommunityPostStatus.PENDING_REVIEW, CommunityPostStatus.HIDDEN);
    private static final String STATUS_MESSAGE = "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.";

    private final AdminCommunityMapper adminCommunityMapper;

    @Transactional(readOnly = true)
    public PageSlice<AdminCommunityPostSummary> listPosts(String status, int page, int size) {
        CommunityPostStatus postStatus = parseStatus(status);
        List<AdminCommunityPostRow> rows = adminCommunityMapper.selectPosts(
                postStatus.name(), PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminCommunityPostRow> slice = PageSlice.of(rows, page, size);
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(
                CommunityReportTargetType.POST,
                slice.content().stream().map(AdminCommunityPostRow::postId).toList());
        return slice.map(row -> new AdminCommunityPostSummary(
                row.postId(),
                row.title(),
                row.content(),
                new AdminCommunityAuthor(row.authorId(), row.authorNickname()),
                row.status(),
                row.reportCount(),
                new AdminCommunityReasonCounts(
                        row.spamCount(),
                        row.abuseCount(),
                        row.illegalCampingCount(),
                        row.privacyCount(),
                        row.moneyScamCount(),
                        row.otherCount()),
                recent.getOrDefault(row.postId(), List.of()),
                row.statusChangedAt()));
    }

    @Transactional(readOnly = true)
    public PageSlice<AdminCommunityCommentSummary> listComments(String status, int page, int size) {
        CommunityPostStatus commentStatus = parseStatus(status);
        List<AdminCommunityCommentRow> rows = adminCommunityMapper.selectComments(
                commentStatus.name(), PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<AdminCommunityCommentRow> slice = PageSlice.of(rows, page, size);
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(
                CommunityReportTargetType.COMMENT,
                slice.content().stream()
                        .map(AdminCommunityCommentRow::commentId)
                        .toList());
        return slice.map(row -> new AdminCommunityCommentSummary(
                row.commentId(),
                row.postId(),
                row.content(),
                new AdminCommunityAuthor(row.authorId(), row.authorNickname()),
                row.status(),
                row.reportCount(),
                new AdminCommunityReasonCounts(
                        row.spamCount(),
                        row.abuseCount(),
                        row.illegalCampingCount(),
                        row.privacyCount(),
                        row.moneyScamCount(),
                        row.otherCount()),
                recent.getOrDefault(row.commentId(), List.of()),
                row.statusChangedAt()));
    }

    private Map<Long, List<AdminCommunityRecentReport>> readRecentReports(
            CommunityReportTargetType targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return adminCommunityMapper.selectRecentReports(targetType.name(), targetIds).stream()
                .collect(Collectors.groupingBy(
                        AdminCommunityRecentReportRow::targetId,
                        Collectors.mapping(
                                row -> new AdminCommunityRecentReport(row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }

    private static CommunityPostStatus parseStatus(String status) {
        if (status == null) {
            return CommunityPostStatus.PENDING_REVIEW;
        }
        return EnumParser.parseAmong(REVIEWABLE_STATUSES, status, STATUS_MESSAGE);
    }
}
