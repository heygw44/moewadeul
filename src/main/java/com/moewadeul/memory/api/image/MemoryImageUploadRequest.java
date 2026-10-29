package com.moewadeul.memory.api.image;

import com.moewadeul.common.image.application.ImageUploadCommand;
import jakarta.validation.constraints.NotNull;

public record MemoryImageUploadRequest(
        @NotNull(message = "사진 형식을 입력해 주세요.") String contentType,
        @NotNull(message = "사진 크기를 입력해 주세요.") Long sizeBytes,
        Long thumbnailSizeBytes) {

    ImageUploadCommand toCommand() {
        return new ImageUploadCommand(contentType, sizeBytes, thumbnailSizeBytes);
    }
}
