package com.moewadeul.community.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.AdminModeration;
import com.moewadeul.common.moderation.Moderatable;
import com.moewadeul.common.moderation.ModerationResult;
import com.moewadeul.common.moderation.ModerationTarget;
import com.moewadeul.community.domain.comment.CommunityComment;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.community.domain.report.CommunityReportRepository;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityModerationService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityReportRepository communityReportRepository;
    private final AdminModeration adminModeration;

    private final ModerationTarget<CommunityPost> postTarget =
            new CommunityTarget<>(AdminAuditTargetType.COMMUNITY_POST, CommunityReportTargetType.POST) {

                @Override
                public CommunityPost lock(long postId) {
                    return communityPostRepository
                            .findByIdForUpdate(postId)
                            .filter(post -> !post.isDeleted())
                            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
                }
            };

    private final ModerationTarget<CommunityComment> commentTarget =
            new CommunityTarget<>(AdminAuditTargetType.COMMUNITY_COMMENT, CommunityReportTargetType.COMMENT) {

                @Override
                public CommunityComment lock(long commentId) {
                    return communityCommentRepository
                            .findByIdForUpdate(commentId)
                            .filter(comment -> !comment.isDeleted())
                            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
                }
            };

    @Transactional
    public ModerationResult hidePost(long postId, long adminId) {
        return adminModeration.hide(postTarget, postId, adminId);
    }

    @Transactional
    public ModerationResult restorePost(long postId, long adminId) {
        return adminModeration.restore(postTarget, postId, adminId);
    }

    @Transactional
    public ModerationResult hideComment(long commentId, long adminId) {
        return adminModeration.hide(commentTarget, commentId, adminId);
    }

    @Transactional
    public ModerationResult restoreComment(long commentId, long adminId) {
        return adminModeration.restore(commentTarget, commentId, adminId);
    }

    private abstract class CommunityTarget<T extends Moderatable> implements ModerationTarget<T> {

        private final String auditTargetType;
        private final CommunityReportTargetType reportTargetType;

        CommunityTarget(String auditTargetType, CommunityReportTargetType reportTargetType) {
            this.auditTargetType = auditTargetType;
            this.reportTargetType = reportTargetType;
        }

        @Override
        public AdminAuditAction hideAction() {
            return AdminAuditAction.COMMUNITY_HIDE;
        }

        @Override
        public AdminAuditAction restoreAction() {
            return AdminAuditAction.COMMUNITY_RESTORE;
        }

        @Override
        public String auditTargetType() {
            return auditTargetType;
        }

        @Override
        public int markReviewed(long targetId, Instant now) {
            return communityReportRepository.markReviewed(reportTargetType, targetId, now);
        }
    }
}
