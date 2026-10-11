package com.moewadeul.review.application;

import java.util.List;

/**
 * 후기 수정 요청. 평점과 내용을 둘 다 담는다. 방문일은 고칠 수 없어서 담지 않는다. imageIds가 null이면 이미지를 그대로 두고, 목록이면 그 순서대로
 * 통째로 바꾼다. 빈 목록이면 이미지를 모두 뗀다.
 */
public record SpotReviewReviseCommand(int rating, String content, List<Long> imageIds) {

    public SpotReviewReviseCommand(int rating, String content) {
        this(rating, content, null);
    }
}
