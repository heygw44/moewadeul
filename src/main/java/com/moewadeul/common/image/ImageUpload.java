package com.moewadeul.common.image;

import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import java.time.Instant;
import java.util.Map;

public record ImageUpload(
        long imageId,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt,
        PresignedUpload thumbnailUpload) {}
