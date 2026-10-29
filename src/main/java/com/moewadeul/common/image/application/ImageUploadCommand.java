package com.moewadeul.common.image.application;

public record ImageUploadCommand(String contentType, long sizeBytes, Long thumbnailSizeBytes) {

    public boolean hasThumbnail() {
        return thumbnailSizeBytes != null;
    }
}
