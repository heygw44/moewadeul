package com.moewadeul.community.application.image;

public record CommunityImageUploadCommand(String contentType, long sizeBytes, Long thumbnailSizeBytes) {

    public boolean hasThumbnail() {
        return thumbnailSizeBytes != null;
    }
}
