package com.moewadeul.review.api;

import com.moewadeul.common.image.application.ImageUploadCommand;
import jakarta.validation.constraints.NotNull;

public record ReviewImageUploadRequest(
        @NotNull(message = "이미지 형식을 입력해 주세요.") String contentType,
        @NotNull(message = "이미지 크기를 입력해 주세요.") Long sizeBytes) {

    ImageUploadCommand toCommand() {
        return new ImageUploadCommand(contentType, sizeBytes, null);
    }
}
