package com.moewadeul.review.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "spot_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotReview {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    public static final int CONTENT_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spot_id")
    private long spotId;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "visited_date")
    private LocalDate visitedDate;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private short rating;

    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private SpotReview(long spotId, long memberId, LocalDate visitedDate, int rating, String content, Instant now) {
        this.spotId = spotId;
        this.memberId = memberId;
        this.visitedDate = visitedDate;
        this.rating = (short) rating;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SpotReview write(
            long spotId, long memberId, LocalDate visitedDate, int rating, String content, Instant now) {
        if (visitedDate == null || now == null) {
            throw new IllegalArgumentException("후기를 만드는 데 필요한 값이 null입니다.");
        }
        if (visitedDate.isAfter(KoreaTime.dateOf(now))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "방문일은 오늘(한국 날짜)보다 뒤일 수 없어요.");
        }
        requireValidBody(rating, content);
        return new SpotReview(spotId, memberId, visitedDate, rating, content, now);
    }

    public void revise(int rating, String content, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("후기를 고친 시각이 null입니다.");
        }
        requireValidBody(rating, content);
        this.rating = (short) rating;
        this.content = content;
        this.updatedAt = now;
    }

    public void requireWrittenBy(long memberId) {
        if (this.memberId != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private static void requireValidBody(int rating, String content) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("평점은 " + MIN_RATING + "~" + MAX_RATING + "이어야 합니다.");
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("후기 내용은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
