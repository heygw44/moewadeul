package com.moewadeul.community.domain.comment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.community.domain.CommunityErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "community_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityComment {

    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id")
    private long postId;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "reply_to_comment_id")
    private Long replyToCommentId;

    private String content;

    @Enumerated(EnumType.STRING)
    private CommunityCommentStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private CommunityComment(
            long postId, long memberId, Long parentId, Long replyToCommentId, String content, Instant now) {
        this.postId = postId;
        this.memberId = memberId;
        this.parentId = parentId;
        this.replyToCommentId = replyToCommentId;
        this.content = content;
        this.status = CommunityCommentStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CommunityComment write(long postId, long memberId, Long parentId, String content, Instant now) {
        requireNow(now);
        requireValidContent(content);
        return new CommunityComment(postId, memberId, parentId, null, content, now);
    }

    public static CommunityComment replyTo(CommunityComment target, long memberId, String content, Instant now) {
        requireNow(now);
        requireValidContent(content);
        if (target.isReply()) {
            return new CommunityComment(target.postId, memberId, target.parentId, target.id, content, now);
        }
        return new CommunityComment(target.postId, memberId, target.id, null, content, now);
    }

    public void revise(String content, Instant now) {
        requireNow(now);
        requireValidContent(content);
        this.content = content;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        requireNow(now);
        if (status == CommunityCommentStatus.DELETED) {
            return;
        }
        this.status = CommunityCommentStatus.DELETED;
        this.content = null;
        this.updatedAt = now;
    }

    public void markPendingReview(Instant now) {
        if (status != CommunityCommentStatus.ACTIVE) {
            throw new IllegalStateException("보이는 댓글만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = CommunityCommentStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    public void hide(Instant now) {
        if (status != CommunityCommentStatus.ACTIVE && status != CommunityCommentStatus.PENDING_REVIEW) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE);
        }
        this.status = CommunityCommentStatus.HIDDEN;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        if (status != CommunityCommentStatus.PENDING_REVIEW && status != CommunityCommentStatus.HIDDEN) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE);
        }
        this.status = CommunityCommentStatus.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isWrittenBy(long memberId) {
        return this.memberId == memberId;
    }

    public boolean isActive() {
        return status == CommunityCommentStatus.ACTIVE;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean belongsTo(long postId) {
        return this.postId == postId;
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("댓글을 쓰거나 고친 시각이 null입니다.");
        }
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("댓글은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
