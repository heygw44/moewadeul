package com.moewadeul.community.application.image;

import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import java.time.Instant;
import java.util.Map;

public record CommunityImageUpload(
        long imageId,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt,
        PresignedUpload thumbnailUpload) {}
