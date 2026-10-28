package com.moewadeul.community.api.post;

import com.moewadeul.common.web.PatchField;
import com.moewadeul.community.application.post.CommunityPostReviseCommand;
import com.moewadeul.community.domain.post.CommunityPost;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CommunityPostUpdateRequest(
        @Schema(
                implementation = String.class,
                maxLength = CommunityPost.TITLE_MAX_LENGTH,
                description = "공백뿐인 값은 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> title,

        @Schema(
                implementation = String.class,
                maxLength = CommunityPost.CONTENT_MAX_LENGTH,
                description = "공백뿐인 값은 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> content,

        @Schema(implementation = Long.class, description = "연결할 장소 ID. null로 보내면 장소 연결을 끊는다.")
        PatchField<Long> spotId,

        @Schema(
                implementation = Long[].class,
                description = "붙일 이미지 ID. 보내면 글의 이미지를 배열 순서대로 통째로 바꾸고, 빈 배열은 이미지를 모두 뗀다. null로 보내면 400이다.")
        PatchField<List<Long>> imageIds) {

    public CommunityPostUpdateRequest {
        title = absentIfNull(title);
        content = absentIfNull(content);
        spotId = absentIfNull(spotId);
        imageIds = absentIfNull(imageIds);
    }

    CommunityPostReviseCommand toCommand() {
        return new CommunityPostReviseCommand(title, content, spotId, imageIds);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}
