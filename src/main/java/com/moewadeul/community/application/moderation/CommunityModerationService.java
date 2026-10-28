package com.moewadeul.community.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.comment.CommunityComment;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.domain.report.CommunityReportRepository;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityModerationService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityReportRepository communityReportRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    public CommunityModerationResult hidePost(long postId, long adminId) {
        CommunityPost post = lockPost(postId);
        CommunityPostStatus before = post.getStatus();
        post.hide(clock.instant());
        recordHide(
                adminId,
                AdminAuditTargetType.COMMUNITY_POST,
                postId,
                before.name(),
                post.getStatus().name());
        log.info("community post hidden postId={} adminId={}", postId, adminId);
        return new CommunityModerationResult(postId, post.getStatus().name());
    }

    @Transactional
    public CommunityModerationResult restorePost(long postId, long adminId) {
        CommunityPost post = lockPost(postId);
        CommunityPostStatus before = post.getStatus();
        Instant now = clock.instant();
        post.restore(now);
        int reviewed = communityReportRepository.markReviewed(CommunityReportTargetType.POST, postId, now);
        recordRestore(
                adminId,
                AdminAuditTargetType.COMMUNITY_POST,
                postId,
                before.name(),
                post.getStatus().name(),
                reviewed);
        log.info("community post restored postId={} adminId={} reviewedReportCount={}", postId, adminId, reviewed);
        return new CommunityModerationResult(postId, post.getStatus().name());
    }

    @Transactional
    public CommunityModerationResult hideComment(long commentId, long adminId) {
        CommunityComment comment = lockComment(commentId);
        CommunityCommentStatus before = comment.getStatus();
        comment.hide(clock.instant());
        recordHide(
                adminId,
                AdminAuditTargetType.COMMUNITY_COMMENT,
                commentId,
                before.name(),
                comment.getStatus().name());
        log.info("community comment hidden commentId={} adminId={}", commentId, adminId);
        return new CommunityModerationResult(commentId, comment.getStatus().name());
    }

    @Transactional
    public CommunityModerationResult restoreComment(long commentId, long adminId) {
        CommunityComment comment = lockComment(commentId);
        CommunityCommentStatus before = comment.getStatus();
        Instant now = clock.instant();
        comment.restore(now);
        int reviewed = communityReportRepository.markReviewed(CommunityReportTargetType.COMMENT, commentId, now);
        recordRestore(
                adminId,
                AdminAuditTargetType.COMMUNITY_COMMENT,
                commentId,
                before.name(),
                comment.getStatus().name(),
                reviewed);
        log.info(
                "community comment restored commentId={} adminId={} reviewedReportCount={}",
                commentId,
                adminId,
                reviewed);
        return new CommunityModerationResult(commentId, comment.getStatus().name());
    }

    private CommunityPost lockPost(long postId) {
        return communityPostRepository
                .findByIdForUpdate(postId)
                .filter(post -> post.getStatus() != CommunityPostStatus.DELETED)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private CommunityComment lockComment(long commentId) {
        return communityCommentRepository
                .findByIdForUpdate(commentId)
                .filter(comment -> comment.getStatus() != CommunityCommentStatus.DELETED)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void recordHide(long adminId, String targetType, long targetId, String from, String to) {
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.COMMUNITY_HIDE,
                targetType,
                targetId,
                new CommunityAuditDetails.Hide(from, to));
    }

    private void recordRestore(
            long adminId, String targetType, long targetId, String from, String to, int reviewedReportCount) {
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.COMMUNITY_RESTORE,
                targetType,
                targetId,
                new CommunityAuditDetails.Restore(from, to, reviewedReportCount));
    }
}
