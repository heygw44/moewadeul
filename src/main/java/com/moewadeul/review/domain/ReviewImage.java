package com.moewadeul.review.domain;

import com.moewadeul.common.image.AttachableImage;
import com.moewadeul.common.image.ImageKind;
import com.moewadeul.common.image.ImageNoun;
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
public class ReviewImage extends AttachableImage {

    public static final int MAX_PER_REVIEW = 3;

    public static final ImageKind KIND = new ImageKind("review", ImageNoun.IMAGE, MAX_PER_REVIEW);

    public static final int MAX_UNATTACHED_PER_MEMBER = KIND.maxUnattachedPerMember();

    private ReviewImage(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        super(KIND, memberId, objectKey, contentType, sizeBytes, now);
    }

    public static ReviewImage issue(long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        return new ReviewImage(memberId, objectKey, contentType, sizeBytes, now);
    }

    @Override
    protected ImageKind kind() {
        return KIND;
    }
}
