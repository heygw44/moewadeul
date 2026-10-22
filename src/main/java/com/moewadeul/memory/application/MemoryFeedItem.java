package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 공개 피드의 한 항목. 피드는 누구에게나 같은 모양이라 작성자 본인의 기록이어도 대략 좌표만 담는다.
 *
 * @param spotId 연결한 장소가 ACTIVE일 때만 값이 있다. 아니면 spotName과 함께 null이다
 * @param excerpt 글 앞 100자. 글이 없으면 null이다
 * @param thumbnailUrl 기록 안 순서가 가장 앞선 사진의 썸네일 보기 URL. 썸네일 없이 올린 사진이면 원본 URL이고, 사진이 없으면 null이다
 * @param imageCount 기록에 붙은 사진 수
 * @param likedByMe 조회한 사람이 좋아요를 눌렀는지. 비회원이 조회하면 null이다
 */
public record MemoryFeedItem(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        ApproximateLocation approximate,
        LocalDate visitedDate,
        int nights,
        String excerpt,
        String thumbnailUrl,
        long imageCount,
        long likeCount,
        Boolean likedByMe,
        Instant createdAt) {}
