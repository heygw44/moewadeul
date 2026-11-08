package com.moewadeul.community.application.post;

import com.moewadeul.common.image.application.ImageAttachments;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
class CommunityImageAttachments extends ImageAttachments<CommunityImage> {

    CommunityImageAttachments(
            CommunityImageRepository communityImageRepository, ImageStorage imageStorage, Clock clock) {
        super(CommunityImage.KIND, communityImageRepository, imageStorage, clock);
    }
}
