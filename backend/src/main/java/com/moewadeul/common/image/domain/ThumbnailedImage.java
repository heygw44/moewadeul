package com.moewadeul.common.image.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ThumbnailedImage extends AttachableImage {

    public static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";

    public static final long MAX_THUMBNAIL_SIZE_BYTES = 256L * 1024;

    @Column(name = "thumbnail_object_key")
    private String thumbnailObjectKey;

    @Column(name = "thumbnail_size_bytes")
    private Integer thumbnailSizeBytes;

    protected ThumbnailedImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        super(memberId, objectKey, contentType, sizeBytes, now);
        if (thumbnail != null) {
            addThumbnail(thumbnail);
        }
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

    public static boolean isAllowedThumbnailSize(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_THUMBNAIL_SIZE_BYTES;
    }

    public boolean hasThumbnail() {
        return thumbnailObjectKey != null;
    }

    @Override
    public Optional<Thumbnail> thumbnail() {
        if (!hasThumbnail()) {
            return Optional.empty();
        }
        return Optional.of(new Thumbnail(thumbnailObjectKey, thumbnailSizeBytes));
    }
}
