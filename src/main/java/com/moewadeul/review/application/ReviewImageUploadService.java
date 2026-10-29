package com.moewadeul.review.application;

import com.moewadeul.common.image.AttachableImage.Thumbnail;
import com.moewadeul.common.image.ImageUpload;
import com.moewadeul.common.image.ImageUploadCommand;
import com.moewadeul.common.image.ImageUploadIssuer;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
import com.moewadeul.review.infra.ReviewImageMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewImageUploadService extends ImageUploadIssuer<ReviewImage> {

    ReviewImageUploadService(
            ReviewImageRepository reviewImageRepository,
            ReviewImageMapper reviewImageMapper,
            ImageStorage imageStorage,
            Clock clock) {
        super(ReviewImage.KIND, reviewImageRepository, reviewImageMapper, imageStorage, clock);
    }

    @Transactional
    public ImageUpload issue(long memberId, String contentType, long sizeBytes) {
        return issueUpload(memberId, new ImageUploadCommand(contentType, sizeBytes, null));
    }

    @Override
    protected ReviewImage newImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return ReviewImage.issue(memberId, objectKey, contentType, sizeBytes, now);
    }
}
