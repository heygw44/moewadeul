package com.moewadeul.review.domain;

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
@Table(name = "review_image")
@AttributeOverride(name = "parentId", column = @Column(name = "review_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage extends ThumbnailedImage {

    public static final int MAX_PER_REVIEW = 3;

    public static final ImageKind KIND = new ImageKind("review", ImageMessages.IMAGE, MAX_PER_REVIEW);

    private ReviewImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        super(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }

    public static ReviewImage issue(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        return issue(memberId, objectKey, contentType, sizeBytes, now, null);
    }

    public static ReviewImage issue(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return new ReviewImage(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }

    @Override
    protected ImageKind kind() {
        return KIND;
    }
}
