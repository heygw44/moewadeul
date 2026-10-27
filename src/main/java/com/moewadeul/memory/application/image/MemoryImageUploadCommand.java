package com.moewadeul.memory.application.image;

public record MemoryImageUploadCommand(String contentType, long sizeBytes, Long thumbnailSizeBytes) {

    public boolean hasThumbnail() {
        return thumbnailSizeBytes != null;
    }
}
