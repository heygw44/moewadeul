package com.moewadeul.community.domain.post;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.PendingReview;
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
@Table(name = "community_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

    public static final int TITLE_MAX_LENGTH = 100;

    public static final int CONTENT_MAX_LENGTH = 10000;

    public static final int POPULAR_LIKE_THRESHOLD = 5;

    public static final int SEARCH_RECENT_POST_LIMIT = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    private String title;

    private String content;

    @Column(name = "spot_id")
    private Long spotId;

    @Enumerated(EnumType.STRING)
    private CommunityPostStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private CommunityPost(long memberId, String title, String content, Long spotId, Instant now) {
        this.memberId = memberId;
        this.title = title;
        this.content = content;
        this.spotId = spotId;
        this.status = CommunityPostStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CommunityPost write(long memberId, String title, String content, Long spotId, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("글을 만드는 데 필요한 값이 null입니다.");
        }
        requireValidTitle(title);
        requireValidContent(content);
        return new CommunityPost(memberId, title, content, spotId, now);
    }

    public void revise(String title, String content, Instant now) {
        requireNow(now);
        if (title == null && content == null) {
            return;
        }
        if (title != null) {
            requireValidTitle(title);
        }
        if (content != null) {
            requireValidContent(content);
        }
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        this.updatedAt = now;
    }

    public void changeSpot(Long spotId, Instant now) {
        requireNow(now);
        this.spotId = spotId;
        this.updatedAt = now;
    }

    public void markEdited(Instant now) {
        requireNow(now);
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        requireNow(now);
        if (status == CommunityPostStatus.DELETED) {
            return;
        }
        this.status = CommunityPostStatus.DELETED;
        this.updatedAt = now;
    }

    public void receiveReport(long unreviewedReportCount, Instant now) {
        if (unreviewedReportCount < PendingReview.REPORT_THRESHOLD) {
            return;
        }
        if (status != CommunityPostStatus.ACTIVE) {
            throw new IllegalStateException("보이는 글만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = CommunityPostStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    public void hide(Instant now) {
        if (status != CommunityPostStatus.ACTIVE && status != CommunityPostStatus.PENDING_REVIEW) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE);
        }
        this.status = CommunityPostStatus.HIDDEN;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        if (status != CommunityPostStatus.PENDING_REVIEW && status != CommunityPostStatus.HIDDEN) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE);
        }
        this.status = CommunityPostStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void requireWrittenBy(long memberId) {
        if (this.memberId != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    public boolean isActive() {
        return status == CommunityPostStatus.ACTIVE;
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("글을 고친 시각이 null입니다.");
        }
    }

    private static void requireValidTitle(String title) {
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 공백뿐이 아닌 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("본문은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
