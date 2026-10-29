package com.moewadeul.community.application.image;

import com.moewadeul.common.image.application.ImageCleanup;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.infra.image.CommunityImageMapper;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class CommunityImageCleanupService extends ImageCleanup {

    CommunityImageCleanupService(CommunityImageMapper communityImageMapper, ImageStorage imageStorage, Clock clock) {
        super(CommunityImage.KIND, communityImageMapper, imageStorage, clock);
    }
}
