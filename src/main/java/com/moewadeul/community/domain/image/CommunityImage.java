package com.moewadeul.community.domain.image;

import com.moewadeul.common.image.ImageKind;
import com.moewadeul.common.image.ImageNoun;
import com.moewadeul.common.image.ThumbnailedImage;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "community_image")
@AttributeOverride(name = "parentId", column = @Column(name = "post_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityImage extends ThumbnailedImage {

    public static final int MAX_PER_POST = 5;

    public static final ImageKind KIND = new ImageKind("community", ImageNoun.IMAGE, MAX_PER_POST);

    public static final int MAX_UNATTACHED_PER_MEMBER = KIND.maxUnattachedPerMember();

    private CommunityImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        super(KIND, memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }

    public static CommunityImage issue(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        return issue(memberId, objectKey, contentType, sizeBytes, now, null);
    }

    public static CommunityImage issue(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return new CommunityImage(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }

    @Override
    protected ImageKind kind() {
        return KIND;
    }
}
