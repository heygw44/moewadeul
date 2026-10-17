package com.moewadeul.community.api.comment;

import com.moewadeul.community.application.comment.CommunityCommentWriteCommand;
import com.moewadeul.community.domain.comment.CommunityComment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// parentId인 댓글이 같은 글의 ACTIVE 댓글인지는 댓글 행을 읽어야 해서 서비스가 검사한다.
public record CommunityCommentCreateRequest(
        @NotBlank(message = "댓글을 입력해 주세요.")
        @Size(max = CommunityComment.CONTENT_MAX_LENGTH, message = "댓글은 1000자 이하여야 해요.")
        String content,

        @Schema(description = "답할 댓글의 ID. 답글이어도 되고, 그러면 서버가 같은 최상위 댓글 아래에 단다. 최상위 댓글이면 보내지 않는다.")
        Long parentId) {

    CommunityCommentWriteCommand toCommand() {
        return new CommunityCommentWriteCommand(content, parentId);
    }
}
