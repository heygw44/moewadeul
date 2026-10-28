package com.moewadeul.community.application.moderation;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import com.moewadeul.community.infra.moderation.AdminCommunityCommentRow;
import com.moewadeul.community.infra.moderation.AdminCommunityMapper;
import com.moewadeul.community.infra.moderation.AdminCommunityPostRow;
import com.moewadeul.community.infra.moderation.AdminCommunityRecentReportRow;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCommunityQueryService {

    private final AdminCommunityMapper adminCommunityMapper;

    @Transactional(readOnly = true)
    public AdminCommunityPage<AdminCommunityPostSummary> listPosts(String status, int page, int size) {
        CommunityPostStatus postStatus = parseStatus(status);
        List<AdminCommunityPostRow> rows =
                adminCommunityMapper.selectPosts(postStatus.name(), (long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminCommunityPostRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(
                CommunityReportTargetType.POST,
                pageRows.stream().map(AdminCommunityPostRow::postId).toList());
        List<AdminCommunityPostSummary> content = pageRows.stream()
                .map(row -> new AdminCommunityPostSummary(
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
                        row.statusChangedAt()))
                .toList();
        return new AdminCommunityPage<>(content, page, size, hasNext);
    }

    @Transactional(readOnly = true)
    public AdminCommunityPage<AdminCommunityCommentSummary> listComments(String status, int page, int size) {
        CommunityPostStatus commentStatus = parseStatus(status);
        List<AdminCommunityCommentRow> rows =
                adminCommunityMapper.selectComments(commentStatus.name(), (long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminCommunityCommentRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<AdminCommunityRecentReport>> recent = readRecentReports(
                CommunityReportTargetType.COMMENT,
                pageRows.stream().map(AdminCommunityCommentRow::commentId).toList());
        List<AdminCommunityCommentSummary> content = pageRows.stream()
                .map(row -> new AdminCommunityCommentSummary(
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
                        row.statusChangedAt()))
                .toList();
        return new AdminCommunityPage<>(content, page, size, hasNext);
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
        for (CommunityPostStatus candidate : List.of(CommunityPostStatus.PENDING_REVIEW, CommunityPostStatus.HIDDEN)) {
            if (candidate.name().equals(status)) {
                return candidate;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.");
    }
}
