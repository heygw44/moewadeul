package com.moewadeul.memory.api.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.common.image.application.ImageUpload;
import java.time.Instant;
import java.util.Map;

public record MemoryImageUploadResponse(
        long imageId,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) ThumbnailUpload thumbnailUpload) {

    private static final String UPLOAD_METHOD = "PUT";

    static MemoryImageUploadResponse from(ImageUpload upload) {
        return new MemoryImageUploadResponse(
                upload.imageId(),
                upload.uploadUrl(),
                UPLOAD_METHOD,
                upload.headers(),
                upload.expiresAt(),
                ThumbnailUpload.from(upload.thumbnailUpload()));
    }

    public record ThumbnailUpload(String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {

        static ThumbnailUpload from(ImageUpload.ThumbnailUpload upload) {
            if (upload == null) {
                return null;
            }
            return new ThumbnailUpload(upload.uploadUrl(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
        }
    }
}
