package com.moewadeul.review.application;

import com.moewadeul.common.image.ImageAttachments;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
import org.springframework.stereotype.Component;

@Component
class ReviewImageAttachments extends ImageAttachments<ReviewImage> {

    ReviewImageAttachments(ReviewImageRepository reviewImageRepository, ImageStorage imageStorage) {
        super(ReviewImage.KIND, reviewImageRepository, imageStorage);
    }
}
