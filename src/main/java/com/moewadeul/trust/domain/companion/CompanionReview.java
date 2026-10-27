package com.moewadeul.trust.domain.companion;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companion_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionReview {

    public static final int COMMENT_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "basecamp_id")
    private long basecampId;

    @Column(name = "reviewer_id")
    private long reviewerId;

    @Column(name = "reviewee_id")
    private long revieweeId;

    @Column(name = "rejoin_wanted")
    private boolean rejoinWanted;

    private String comment;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "companion_review_tag", joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", length = 30)
    private Set<CompanionReviewTag> tags = EnumSet.noneOf(CompanionReviewTag.class);

    private CompanionReview(
            long basecampId,
            long reviewerId,
            long revieweeId,
            boolean rejoinWanted,
            Collection<CompanionReviewTag> tags,
            String comment,
            Instant now) {
        this.basecampId = basecampId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rejoinWanted = rejoinWanted;
        this.tags = tags.isEmpty() ? EnumSet.noneOf(CompanionReviewTag.class) : EnumSet.copyOf(tags);
        this.comment = comment;
        this.createdAt = now;
    }

    public static CompanionReview write(
            long basecampId,
            long reviewerId,
            long revieweeId,
            boolean rejoinWanted,
            Collection<CompanionReviewTag> tags,
            String comment,
            Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("후기를 쓴 시각이 null입니다.");
        }
        if (reviewerId == revieweeId) {
            throw new IllegalArgumentException("자기 자신에게는 후기를 쓸 수 없습니다.");
        }
        if (comment != null && comment.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException("코멘트는 " + COMMENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        String normalizedComment = comment == null || comment.isBlank() ? null : comment;
        Collection<CompanionReviewTag> safeTags = tags == null ? Set.of() : tags;
        return new CompanionReview(basecampId, reviewerId, revieweeId, rejoinWanted, safeTags, normalizedComment, now);
    }

    public void hide(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("후기를 숨기는 시각이 null입니다.");
        }
        if (hiddenAt == null) {
            this.hiddenAt = now;
        }
    }
}
