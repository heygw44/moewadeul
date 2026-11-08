package com.moewadeul.common.image.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AttachableImage {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public static final Duration UNATTACHED_RETENTION = Duration.ofHours(24);

    // 정리 작업은 객체를 지운 뒤 붙지 않은 행만 지운다. 정리 대상이 되기 한 시간 전부터 붙이지 못하게 해, 객체가 지워지는 사진이 글에 붙는 일을 막는다.
    // 업로드 상한도 이 기간으로 세어, 더는 붙일 수 없는 사진이 자리를 차지하지 않게 한다.
    public static final Duration ATTACHABLE_FOR = UNATTACHED_RETENTION.minusHours(1);

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    private Long parentId;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private int sizeBytes;

    // 컬럼이 TINYINT라서 Hibernate의 스키마 검증을 통과하려면 JDBC 타입을 맞춰야 한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "ever_attached")
    private boolean everAttached;

    protected AttachableImage(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        ImageMessages messages = kind().messages();
        if (objectKey == null || objectKey.isBlank() || now == null) {
            throw new IllegalArgumentException(messages.missingValue());
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException(messages.disallowedType());
        }
        if (!isAllowedSize(sizeBytes)) {
            throw new IllegalArgumentException(messages.disallowedSize(MAX_SIZE_BYTES));
        }
        this.memberId = memberId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = (int) sizeBytes;
        this.createdAt = now;
    }

    protected abstract ImageKind kind();

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && EXTENSIONS.containsKey(contentType);
    }

    public static boolean isAllowedSize(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_SIZE_BYTES;
    }

    public static Optional<String> extensionOf(String contentType) {
        return Optional.ofNullable(contentType).map(EXTENSIONS::get);
    }

    public void attachTo(long parentId, int order) {
        ImageKind kind = kind();
        if (order < 0 || order >= kind.maxPerParent()) {
            throw new IllegalArgumentException(kind.messages().orderOutOfRange(kind.maxPerParent()));
        }
        this.parentId = parentId;
        this.displayOrder = order;
        this.everAttached = true;
    }

    public void detach() {
        this.parentId = null;
        this.displayOrder = null;
    }

    public boolean isUploadedBy(long memberId) {
        return this.memberId == memberId;
    }

    public boolean isAttachedTo(long parentId) {
        return this.parentId != null && this.parentId == parentId;
    }

    public boolean isAttachableTo(Long parentId, Instant now) {
        if (isAttached()) {
            return parentId != null && isAttachedTo(parentId);
        }
        return createdAt.isAfter(now.minus(ATTACHABLE_FOR));
    }

    public boolean isAttached() {
        return parentId != null;
    }

    public Optional<Thumbnail> thumbnail() {
        return Optional.empty();
    }

    public record Thumbnail(String objectKey, long sizeBytes) {}
}
