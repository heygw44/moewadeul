package com.moewadeul.community.api.image;

import com.moewadeul.common.image.ImageUploadCommand;
import com.moewadeul.community.domain.image.CommunityImage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CommunityImageUploadRequest(
        @NotNull(message = "이미지 형식을 입력해 주세요.")
        @Pattern(regexp = "image/(jpeg|png|webp)", message = "이미지 형식은 image/jpeg, image/png, image/webp 중 하나여야 해요.")
        String contentType,

        @NotNull(message = "이미지 크기를 입력해 주세요.")
        @Min(value = 1, message = "이미지 크기는 1바이트 이상이어야 해요.")
        @Max(value = CommunityImage.MAX_SIZE_BYTES, message = "이미지 크기는 5MB(5242880바이트) 이하여야 해요.")
        Long sizeBytes,

        @Min(value = 1, message = "썸네일 크기는 1바이트 이상이어야 해요.")
        @Max(value = CommunityImage.MAX_THUMBNAIL_SIZE_BYTES, message = "썸네일 크기는 256KB 이하여야 해요.")
        Long thumbnailSizeBytes) {

    ImageUploadCommand toCommand() {
        return new ImageUploadCommand(contentType, sizeBytes, thumbnailSizeBytes);
    }
}
