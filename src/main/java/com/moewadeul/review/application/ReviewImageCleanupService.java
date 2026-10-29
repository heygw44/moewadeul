package com.moewadeul.review.application;

import com.moewadeul.common.image.application.ImageCleanup;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.infra.ReviewImageMapper;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class ReviewImageCleanupService extends ImageCleanup {

    ReviewImageCleanupService(ReviewImageMapper reviewImageMapper, ImageStorage imageStorage, Clock clock) {
        super(ReviewImage.KIND, reviewImageMapper, imageStorage, clock);
    }
}
