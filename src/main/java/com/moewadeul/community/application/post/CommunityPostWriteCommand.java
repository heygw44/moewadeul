package com.moewadeul.community.application.post;

import java.util.List;

public record CommunityPostWriteCommand(String title, String content, Long spotId, List<Long> imageIds) {

    public CommunityPostWriteCommand {
        imageIds = imageIds == null ? List.of() : List.copyOf(imageIds);
    }

    public CommunityPostWriteCommand(String title, String content, Long spotId) {
        this(title, content, spotId, List.of());
    }
}
