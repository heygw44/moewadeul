package com.moewadeul.common.image;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
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

    public boolean isAttached() {
        return parentId != null;
    }

    public Optional<Thumbnail> thumbnail() {
        return Optional.empty();
    }

    public record Thumbnail(String objectKey, long sizeBytes) {}
}
