package com.moewadeul.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "review_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public static final int MAX_PER_REVIEW = 3;

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private int sizeBytes;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at")
    private Instant createdAt;

    private ReviewImage(long memberId, String objectKey, String contentType, int sizeBytes, Instant now) {
        this.memberId = memberId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    public static ReviewImage issue(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        if (objectKey == null || objectKey.isBlank() || now == null) {
            throw new IllegalArgumentException("이미지를 만드는 데 필요한 값이 비었습니다.");
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("허용하지 않는 이미지 형식입니다.");
        }
        if (!isAllowedSize(sizeBytes)) {
            throw new IllegalArgumentException("이미지 크기는 1바이트 이상 " + MAX_SIZE_BYTES + "바이트 이하여야 합니다.");
        }
        return new ReviewImage(memberId, objectKey, contentType, (int) sizeBytes, now);
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && EXTENSIONS.containsKey(contentType);
    }

    public static boolean isAllowedSize(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_SIZE_BYTES;
    }

    public static Optional<String> extensionOf(String contentType) {
        return Optional.ofNullable(contentType).map(EXTENSIONS::get);
    }

    public void attachTo(long reviewId, int order) {
        if (order < 0 || order >= MAX_PER_REVIEW) {
            throw new IllegalArgumentException("이미지 순서는 0 이상 " + MAX_PER_REVIEW + " 미만이어야 합니다.");
        }
        this.reviewId = reviewId;
        this.displayOrder = order;
    }

    public void detach() {
        this.reviewId = null;
        this.displayOrder = null;
    }

    public boolean isUploadedBy(long memberId) {
        return this.memberId == memberId;
    }

    public boolean isAttachedTo(long reviewId) {
        return this.reviewId != null && this.reviewId == reviewId;
    }

    public boolean isAttached() {
        return reviewId != null;
    }
}
