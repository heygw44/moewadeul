package com.moewadeul.community.api.post;

import com.moewadeul.community.application.post.CommunityPostWriteCommand;
import com.moewadeul.community.domain.post.CommunityPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CommunityPostCreateRequest(
        @NotBlank(message = "제목을 입력해 주세요.") @Size(max = CommunityPost.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 해요.")
        String title,

        @NotBlank(message = "본문을 입력해 주세요.")
        @Size(max = CommunityPost.CONTENT_MAX_LENGTH, message = "본문은 10000자 이하여야 해요.")
        String content,

        @Schema(description = "연결할 장소 ID. 연결하지 않으면 보내지 않는다.")
        Long spotId,

        @Schema(description = "붙일 이미지 ID. 최대 5개이고 배열 순서가 글 안 순서다. 붙이지 않으면 보내지 않는다.")
        List<@NotNull(message = "이미지 ID는 비워 둘 수 없어요.") Long> imageIds) {

    CommunityPostWriteCommand toCommand() {
        return new CommunityPostWriteCommand(title, content, spotId, imageIds);
    }
}
