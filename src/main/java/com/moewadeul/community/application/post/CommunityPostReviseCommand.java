package com.moewadeul.community.application.post;

import com.moewadeul.common.web.PatchField;
import java.util.List;
import java.util.Objects;

public record CommunityPostReviseCommand(
        PatchField<String> title,
        PatchField<String> content,
        PatchField<Long> spotId,
        PatchField<List<Long>> imageIds) {

    public CommunityPostReviseCommand {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(spotId, "spotId");
        Objects.requireNonNull(imageIds, "imageIds");
    }

    public CommunityPostReviseCommand(PatchField<String> title, PatchField<String> content, PatchField<Long> spotId) {
        this(title, content, spotId, PatchField.absent());
    }
}
