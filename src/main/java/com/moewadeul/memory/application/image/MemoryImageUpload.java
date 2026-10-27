package com.moewadeul.memory.application.image;

import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import java.time.Instant;
import java.util.Map;

public record MemoryImageUpload(
        long imageId,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt,
        PresignedUpload thumbnailUpload) {}
