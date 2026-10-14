package com.moewadeul.spot.application.detail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 장소 상세에 싣는 최근 후기 한 건. authorNickname은 조회한 시점의 닉네임이라서, 탈퇴한 회원이면 익명 닉네임이다. images는 후기 안 순서의 이미지이고,
 * 없으면 빈 목록이다.
 */
public record SpotRecentReview(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        List<Image> images,
        Instant createdAt) {

    /** 후기에 붙은 이미지 한 장. url은 서버가 발급한 사전 서명 조회 URL이다. */
    public record Image(long imageId, String url) {}
}
