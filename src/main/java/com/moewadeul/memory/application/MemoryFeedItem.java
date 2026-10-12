package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 공개 피드의 한 항목. 피드는 누구에게나 같은 모양이라 작성자 본인의 기록이어도 대략 좌표만 담는다.
 *
 * @param spotId 연결한 장소가 ACTIVE일 때만 값이 있다. 아니면 spotName과 함께 null이다
 * @param excerpt 글 앞 100자. 글이 없으면 null이다
 * @param images 기록 안 순서대로 담은 사진. 없으면 빈 목록이다
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
        List<MemoryPhoto> images,
        long likeCount,
        Boolean likedByMe,
        Instant createdAt) {}
