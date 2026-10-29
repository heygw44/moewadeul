package com.moewadeul.community.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.ReportIntake;
import com.moewadeul.community.domain.CommunityErrorCode;
import com.moewadeul.community.domain.comment.CommunityComment;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.community.domain.report.CommunityReport;
import com.moewadeul.community.domain.report.CommunityReportRepository;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityReportService {

    private static final ReportIntake REPORT_INTAKE =
            new ReportIntake("uk_community_report_target_reporter", CommunityErrorCode.COMMUNITY_ALREADY_REPORTED);

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityReportRepository communityReportRepository;
    private final Clock clock;

    @Transactional
    public void reportPost(long memberId, long postId, CommunityReportCommand command) {
        CommunityPost post = communityPostRepository
                .findByIdForUpdate(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        long unreviewedReportCount = saveReport(CommunityReportTargetType.POST, post.getId(), memberId, command);
        post.receiveReport(unreviewedReportCount, clock.instant());
    }

    @Transactional
    public void reportComment(long memberId, long commentId, CommunityReportCommand command) {
        CommunityComment comment = communityCommentRepository
                .findByIdForUpdate(commentId)
                .filter(CommunityComment::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        communityPostRepository
                .findById(comment.getPostId())
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        long unreviewedReportCount = saveReport(CommunityReportTargetType.COMMENT, comment.getId(), memberId, command);
        comment.receiveReport(unreviewedReportCount, clock.instant());
    }

    private long saveReport(
            CommunityReportTargetType targetType, long targetId, long memberId, CommunityReportCommand command) {
        return REPORT_INTAKE.accept(
                () -> communityReportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                        targetType, targetId, memberId),
                () -> communityReportRepository.saveAndFlush(CommunityReport.of(
                        targetType, targetId, memberId, command.reason(), command.content(), clock.instant())),
                () -> communityReportRepository.countByTargetTypeAndTargetIdAndReviewedAtIsNull(targetType, targetId));
    }
}
