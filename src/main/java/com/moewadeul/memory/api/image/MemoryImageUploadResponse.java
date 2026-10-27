package com.moewadeul.memory.api.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.memory.application.image.MemoryImageUpload;
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

    static MemoryImageUploadResponse from(MemoryImageUpload upload) {
        return new MemoryImageUploadResponse(
                upload.imageId(),
                upload.uploadUrl(),
                UPLOAD_METHOD,
                upload.headers(),
                upload.expiresAt(),
                ThumbnailUpload.from(upload.thumbnailUpload()));
    }

    public record ThumbnailUpload(String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {

        static ThumbnailUpload from(PresignedUpload upload) {
            if (upload == null) {
                return null;
            }
            return new ThumbnailUpload(upload.url(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
        }
    }
}
