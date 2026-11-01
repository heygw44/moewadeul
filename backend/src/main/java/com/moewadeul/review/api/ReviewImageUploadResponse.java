package com.moewadeul.review.api;

import com.moewadeul.common.image.application.ImageUpload;
import java.time.Instant;
import java.util.Map;

public record ReviewImageUploadResponse(
        long imageId, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {

    private static final String UPLOAD_METHOD = "PUT";

    static ReviewImageUploadResponse from(ImageUpload upload) {
        return new ReviewImageUploadResponse(
                upload.imageId(), upload.uploadUrl(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
    }
}
