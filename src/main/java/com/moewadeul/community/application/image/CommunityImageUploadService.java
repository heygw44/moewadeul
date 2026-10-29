package com.moewadeul.community.application.image;

import com.moewadeul.common.image.AttachableImage.Thumbnail;
import com.moewadeul.common.image.ImageUpload;
import com.moewadeul.common.image.ImageUploadCommand;
import com.moewadeul.common.image.ImageUploadIssuer;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import com.moewadeul.community.infra.image.CommunityImageMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityImageUploadService extends ImageUploadIssuer<CommunityImage> {

    CommunityImageUploadService(
            CommunityImageRepository communityImageRepository,
            CommunityImageMapper communityImageMapper,
            ImageStorage imageStorage,
            Clock clock) {
        super(CommunityImage.KIND, communityImageRepository, communityImageMapper, imageStorage, clock);
    }

    @Transactional
    public ImageUpload issue(long memberId, ImageUploadCommand command) {
        return issueUpload(memberId, command);
    }

    @Override
    protected CommunityImage newImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return CommunityImage.issue(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }
}
