package com.moewadeul.memory.application.image;

import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import java.time.Instant;
import java.util.Map;

/**
 * 발급한 사진 업로드 URL. 회원은 uploadUrl로 파일을 PUT하면서 headers를 그대로 보낸다.
 *
 * @param imageId 기록을 쓰거나 고칠 때 imageIds에 넣을 사진 ID
 * @param headers 서명에 들어간 Content-Type과 Content-Length
 * @param expiresAt uploadUrl을 더 쓸 수 없게 되는 시각
 * @param thumbnailUpload 썸네일을 올릴 URL과 헤더. 요청에 썸네일 크기가 없었으면 null이다
 */
public record MemoryImageUpload(
        long imageId,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt,
        PresignedUpload thumbnailUpload) {}
