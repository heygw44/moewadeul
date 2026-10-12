package com.moewadeul.memory.infra;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 기록 상세 조회가 읽은 기록 한 건과 작성자의 현재 닉네임, 보이는 연결 장소. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로
 * 바꾼 것과 같아야 한다.
 *
 * <p>spotId와 spotName은 연결한 장소가 없거나 ACTIVE가 아니면 null이다. content는 글이 없으면 null이다. visibility는 공개 범위 이름이다.
 * likedByMe는 조회한 사람이 회원일 때만 값이 있고, 비회원이면 null이다.
 */
public record MemoryRow(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String content,
        String visibility,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        Boolean likedByMe) {}
