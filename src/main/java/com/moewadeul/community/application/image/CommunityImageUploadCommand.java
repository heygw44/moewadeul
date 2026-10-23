package com.moewadeul.community.application.image;

/**
 * 이미지 업로드 URL 발급 요청. 값은 받은 그대로 담고, {@link CommunityImageUploadService}가 규칙을 검사한다.
 *
 * @param contentType 원본 이미지의 형식
 * @param sizeBytes 원본 이미지의 크기(바이트)
 * @param thumbnailSizeBytes 브라우저가 만든 JPEG 썸네일의 크기(바이트). 썸네일을 만들지 못했으면 null이다
 */
public record CommunityImageUploadCommand(String contentType, long sizeBytes, Long thumbnailSizeBytes) {

    /** 썸네일 업로드 URL도 함께 발급해야 하면 true다. */
    public boolean hasThumbnail() {
        return thumbnailSizeBytes != null;
    }
}
