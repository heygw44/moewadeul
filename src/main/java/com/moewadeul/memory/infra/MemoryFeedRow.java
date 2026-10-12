package com.moewadeul.memory.infra;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 공개 피드의 한 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>latitude와 longitude는 정확한 좌표라서 응답으로 내보내기 전에 반드시 대략 좌표로 바꾼다. spotId와 spotName은 연결한 장소가 없거나 ACTIVE가
 * 아니면 null이다. excerpt는 글 앞 100자이고 글이 없으면 null이다. likedByMe는 조회한 사람이 회원일 때만 값이 있고, 비회원이면 null이다.
 */
public record MemoryFeedRow(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String excerpt,
        long likeCount,
        Boolean likedByMe,
        Instant createdAt) {}
