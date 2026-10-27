package com.moewadeul.memory.domain.image;

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
@Table(name = "memory_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryImage {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";

    public static final long MAX_THUMBNAIL_SIZE_BYTES = 256L * 1024;

    public static final int MAX_PER_MEMORY = 10;

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "memory_id")
    private Long memoryId;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private int sizeBytes;

    @Column(name = "thumbnail_object_key")
    private String thumbnailObjectKey;

    @Column(name = "thumbnail_size_bytes")
    private Integer thumbnailSizeBytes;

    // 컬럼이 TINYINT라서 Hibernate의 스키마 검증을 통과하려면 JDBC 타입을 맞춰야 한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at")
    private Instant createdAt;

    private MemoryImage(long memberId, String objectKey, String contentType, int sizeBytes, Instant now) {
        this.memberId = memberId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    public static MemoryImage issue(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        return issue(memberId, objectKey, contentType, sizeBytes, now, null);
    }

    public static MemoryImage issue(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        if (objectKey == null || objectKey.isBlank() || now == null) {
            throw new IllegalArgumentException("사진을 만드는 데 필요한 값이 비었습니다.");
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("허용하지 않는 사진 형식입니다.");
        }
        if (!isAllowedSize(sizeBytes)) {
            throw new IllegalArgumentException("사진 크기는 1바이트 이상 " + MAX_SIZE_BYTES + "바이트 이하여야 합니다.");
        }
        MemoryImage image = new MemoryImage(memberId, objectKey, contentType, (int) sizeBytes, now);
        if (thumbnail != null) {
            image.addThumbnail(thumbnail);
        }
        return image;
    }

    private void addThumbnail(Thumbnail thumbnail) {
        if (thumbnail.objectKey() == null || thumbnail.objectKey().isBlank()) {
            throw new IllegalArgumentException("썸네일 키가 비었습니다.");
        }
        if (!isAllowedThumbnailSize(thumbnail.sizeBytes())) {
            throw new IllegalArgumentException("썸네일 크기는 1바이트 이상 " + MAX_THUMBNAIL_SIZE_BYTES + "바이트 이하여야 합니다.");
        }
        this.thumbnailObjectKey = thumbnail.objectKey();
        this.thumbnailSizeBytes = (int) thumbnail.sizeBytes();
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && EXTENSIONS.containsKey(contentType);
    }

    public static boolean isAllowedSize(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_SIZE_BYTES;
    }

    public static boolean isAllowedThumbnailSize(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_THUMBNAIL_SIZE_BYTES;
    }

    public static Optional<String> extensionOf(String contentType) {
        return Optional.ofNullable(contentType).map(EXTENSIONS::get);
    }

    public void attachTo(long memoryId, int order) {
        if (order < 0 || order >= MAX_PER_MEMORY) {
            throw new IllegalArgumentException("사진 순서는 0 이상 " + MAX_PER_MEMORY + " 미만이어야 합니다.");
        }
        this.memoryId = memoryId;
        this.displayOrder = order;
    }

    public void detach() {
        this.memoryId = null;
        this.displayOrder = null;
    }

    public boolean isUploadedBy(long memberId) {
        return this.memberId == memberId;
    }

    public boolean isAttachedTo(long memoryId) {
        return this.memoryId != null && this.memoryId == memoryId;
    }

    public boolean isAttached() {
        return memoryId != null;
    }

    public boolean hasThumbnail() {
        return thumbnailObjectKey != null;
    }

    public record Thumbnail(String objectKey, long sizeBytes) {}
}
