package com.moewadeul.community.api.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.common.image.ImageUpload;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import java.time.Instant;
import java.util.Map;

public record CommunityImageUploadResponse(
        long imageId,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) ThumbnailUpload thumbnailUpload) {

    private static final String UPLOAD_METHOD = "PUT";

    static CommunityImageUploadResponse from(ImageUpload upload) {
        return new CommunityImageUploadResponse(
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
