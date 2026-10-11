package com.moewadeul.review.api;

import com.moewadeul.review.domain.ReviewImage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReviewImageUploadRequest(
        @NotNull(message = "이미지 형식을 입력해야 합니다.")
        @Pattern(regexp = "image/(jpeg|png|webp)", message = "이미지 형식은 image/jpeg, image/png, image/webp 중 하나여야 합니다.")
        String contentType,

        @NotNull(message = "이미지 크기를 입력해야 합니다.")
        @Min(value = 1, message = "이미지 크기는 1바이트 이상이어야 합니다.")
        @Max(value = ReviewImage.MAX_SIZE_BYTES, message = "이미지 크기는 5MB(5242880바이트) 이하여야 합니다.")
        Long sizeBytes) {}
