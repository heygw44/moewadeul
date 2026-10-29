package com.moewadeul.community.application.post;

import com.moewadeul.common.image.ImageAttachments;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import org.springframework.stereotype.Component;

@Component
class CommunityImageAttachments extends ImageAttachments<CommunityImage> {

    CommunityImageAttachments(CommunityImageRepository communityImageRepository, ImageStorage imageStorage) {
        super(CommunityImage.KIND, communityImageRepository, imageStorage);
    }
}
