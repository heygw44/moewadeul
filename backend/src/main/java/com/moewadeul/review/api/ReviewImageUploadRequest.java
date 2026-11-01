package com.moewadeul.review.api;

import com.moewadeul.common.image.application.ImageUploadCommand;
import com.moewadeul.common.image.domain.AttachableImage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ReviewImageUploadRequest(
        @Schema(pattern = "image/(jpeg|png|webp)") @NotNull(message = "이미지 형식을 입력해 주세요.")
        String contentType,

        @Schema(minimum = "1", maximum = "" + AttachableImage.MAX_SIZE_BYTES) @NotNull(message = "이미지 크기를 입력해 주세요.")
        Long sizeBytes) {

    ImageUploadCommand toCommand() {
        return new ImageUploadCommand(contentType, sizeBytes, null);
    }
}
