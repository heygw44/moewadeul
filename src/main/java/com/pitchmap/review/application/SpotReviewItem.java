package com.pitchmap.review.application;

import com.pitchmap.review.infra.SpotReviewRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 후기 한 건과 작성자, 붙은 이미지. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다. images는 후기 안 순서이고,
 * 이미지가 없으면 빈 목록이다.
 */
public record SpotReviewItem(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        List<SpotReviewImage> images,
        Instant createdAt) {

    static SpotReviewItem from(SpotReviewRow row, List<SpotReviewImage> images) {
        return new SpotReviewItem(
                row.reviewId(),
                row.authorId(),
                row.authorNickname(),
                row.visitedDate(),
                row.rating(),
                row.content(),
                List.copyOf(images),
                row.createdAt());
    }
}
