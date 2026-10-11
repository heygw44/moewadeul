package com.moewadeul.review.application;

import java.time.LocalDate;
import java.util.List;

/**
 * 후기 작성 요청. visitedDate는 한국 날짜이고, 평점은 1~5이며, 내용은 공백뿐일 수 없다. imageIds는 붙일 이미지를 후기 안 순서대로 담고, 붙일
 * 이미지가 없으면 빈 목록이다.
 */
public record SpotReviewWriteCommand(LocalDate visitedDate, int rating, String content, List<Long> imageIds) {

    public SpotReviewWriteCommand {
        imageIds = imageIds == null ? List.of() : imageIds;
    }

    public SpotReviewWriteCommand(LocalDate visitedDate, int rating, String content) {
        this(visitedDate, rating, content, List.of());
    }
}
