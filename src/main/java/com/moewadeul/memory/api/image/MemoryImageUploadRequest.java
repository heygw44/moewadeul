package com.moewadeul.memory.api.image;

import com.moewadeul.common.image.application.ImageUploadCommand;
import com.moewadeul.common.image.domain.AttachableImage;
import com.moewadeul.common.image.domain.ThumbnailedImage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MemoryImageUploadRequest(
        @Schema(pattern = "image/(jpeg|png|webp)") @NotNull(message = "사진 형식을 입력해 주세요.")
        String contentType,

        @Schema(minimum = "1", maximum = "" + AttachableImage.MAX_SIZE_BYTES) @NotNull(message = "사진 크기를 입력해 주세요.")
        Long sizeBytes,

        @Schema(minimum = "1", maximum = "" + ThumbnailedImage.MAX_THUMBNAIL_SIZE_BYTES)
        Long thumbnailSizeBytes) {

    ImageUploadCommand toCommand() {
        return new ImageUploadCommand(contentType, sizeBytes, thumbnailSizeBytes);
    }
}
