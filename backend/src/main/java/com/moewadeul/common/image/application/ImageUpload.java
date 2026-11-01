package com.moewadeul.common.image.application;

import java.time.Instant;
import java.util.Map;

public record ImageUpload(
        long imageId,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt,
        ThumbnailUpload thumbnailUpload) {

    public record ThumbnailUpload(String uploadUrl, Map<String, String> headers, Instant expiresAt) {}
}
