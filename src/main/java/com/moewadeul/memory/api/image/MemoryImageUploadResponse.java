package com.moewadeul.memory.api.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.memory.application.image.MemoryImageUpload;
import java.time.Instant;
import java.util.Map;

// 화면은 uploadUrl로 PUT하면서 headers를 그대로 보낸다. 저장소 객체 키는 내보내지 않는다.
// thumbnailUpload는 요청에 썸네일 크기가 있었을 때만 키째로 넣는다.
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

    /** 썸네일을 올릴 URL. 화면은 원본과 같은 방식으로 uploadUrl에 PUT하면서 headers를 그대로 보낸다. */
    public record ThumbnailUpload(String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {

        static ThumbnailUpload from(PresignedUpload upload) {
            if (upload == null) {
                return null;
            }
            return new ThumbnailUpload(upload.url(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
        }
    }
}
