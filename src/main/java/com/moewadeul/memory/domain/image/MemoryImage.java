package com.moewadeul.memory.domain.image;

import com.moewadeul.common.image.domain.ImageKind;
import com.moewadeul.common.image.domain.ImageMessages;
import com.moewadeul.common.image.domain.ThumbnailedImage;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memory_image")
@AttributeOverride(name = "parentId", column = @Column(name = "memory_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryImage extends ThumbnailedImage {

    public static final int MAX_PER_MEMORY = 10;

    public static final ImageKind KIND = new ImageKind("memory", ImageMessages.PHOTO, MAX_PER_MEMORY);

    private MemoryImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        super(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }

    public static MemoryImage issue(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        return issue(memberId, objectKey, contentType, sizeBytes, now, null);
    }

    public static MemoryImage issue(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return new MemoryImage(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }

    @Override
    protected ImageKind kind() {
        return KIND;
    }
}
