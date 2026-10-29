package com.moewadeul.community.application.moderation;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
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
import java.util.Optional;
import java.util.function.LongFunction;
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

    private final ModerationTarget<CommunityPost> postTarget = new CommunityTarget<>(
            AdminAuditTargetType.COMMUNITY_POST, CommunityReportTargetType.POST, this::lockAlivePost);

    private final ModerationTarget<CommunityComment> commentTarget = new CommunityTarget<>(
            AdminAuditTargetType.COMMUNITY_COMMENT, CommunityReportTargetType.COMMENT, this::lockAliveComment);

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

    private Optional<CommunityPost> lockAlivePost(long postId) {
        return communityPostRepository.findByIdForUpdate(postId).filter(post -> !post.isDeleted());
    }

    private Optional<CommunityComment> lockAliveComment(long commentId) {
        return communityCommentRepository.findByIdForUpdate(commentId).filter(comment -> !comment.isDeleted());
    }

    private final class CommunityTarget<T extends Moderatable> implements ModerationTarget<T> {

        private final AdminAuditTargetType auditTargetType;
        private final CommunityReportTargetType reportTargetType;
        private final LongFunction<Optional<T>> lockAlive;

        CommunityTarget(
                AdminAuditTargetType auditTargetType,
                CommunityReportTargetType reportTargetType,
                LongFunction<Optional<T>> lockAlive) {
            this.auditTargetType = auditTargetType;
            this.reportTargetType = reportTargetType;
            this.lockAlive = lockAlive;
        }

        @Override
        public T lock(long id) {
            return lockAlive.apply(id).orElseThrow(BusinessException::notFound);
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
        public AdminAuditTargetType auditTargetType() {
            return auditTargetType;
        }

        @Override
        public int markReviewed(long targetId, Instant now) {
            return communityReportRepository.markReviewed(reportTargetType, targetId, now);
        }
    }
}
