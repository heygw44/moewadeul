package com.moewadeul.review.api;

import com.moewadeul.review.application.SpotReviewReviseCommand;
import com.moewadeul.review.domain.SpotReview;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SpotReviewUpdateRequest(
        @NotNull(message = "평점을 입력해 주세요.")
        @Min(value = SpotReview.MIN_RATING, message = "평점은 1 이상이어야 해요.")
        @Max(value = SpotReview.MAX_RATING, message = "평점은 5 이하여야 해요.")
        Integer rating,

        @NotBlank(message = "후기 내용을 입력해 주세요.")
        @Size(max = SpotReview.CONTENT_MAX_LENGTH, message = "후기 내용은 2000자 이하여야 해요.")
        String content,

        @Schema(description = "바꿀 이미지 ID 목록. 보내면 그 순서대로 통째로 바꾸고, 보내지 않으면 그대로 둔다. 빈 배열이면 모두 뗀다.")
        List<Long> imageIds) {

    SpotReviewReviseCommand toCommand() {
        return new SpotReviewReviseCommand(rating, content, imageIds);
    }
}
