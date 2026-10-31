package com.moewadeul.review.api;

import com.moewadeul.review.application.SpotReviewWriteCommand;
import com.moewadeul.review.domain.SpotReview;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record SpotReviewCreateRequest(
        @Schema(description = "방문일(한국 날짜). 오늘보다 뒤일 수 없다.", example = "2026-10-05") @NotNull(message = "방문한 날짜를 골라 주세요.")
        LocalDate visitedDate,

        @NotNull(message = "평점을 골라 주세요.")
        @Min(value = SpotReview.MIN_RATING, message = "평점은 1 이상이어야 해요.")
        @Max(value = SpotReview.MAX_RATING, message = "평점은 5 이하여야 해요.")
        Integer rating,

        @NotBlank(message = "후기 내용을 입력해 주세요.")
        @Size(max = SpotReview.CONTENT_MAX_LENGTH, message = "후기 내용은 2000자 이하여야 해요.")
        String content,

        @Schema(description = "붙일 이미지 ID. 최대 3개이고 배열 순서가 후기 안 순서다. 없으면 이미지 없이 쓴다.")
        List<Long> imageIds) {

    SpotReviewWriteCommand toCommand() {
        return new SpotReviewWriteCommand(visitedDate, rating, content, imageIds);
    }
}
