package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 기록 한 건을 보여 줄 때 필요한 값. 정확한 좌표는 작성자가 볼 때만 담고, 작성자가 아니면 null이라 api 계층이 실수로 내보낼 수 없다.
 *
 * @param spotId 연결한 장소가 ACTIVE일 때만 값이 있다. 아니면 spotName과 함께 null이다
 * @param content 글이 없으면 null이다
 * @param latitude 정확한 위도. 조회한 사람이 작성자일 때만 값이 있다
 * @param longitude 정확한 경도. 조회한 사람이 작성자일 때만 값이 있다
 * @param approximate 정확한 좌표를 소수 둘째 자리로 반올림한 좌표. 누가 보든 담는다
 * @param status 신고·관리자 조치 상태 이름. 작성자가 아닌 사람에게는 ACTIVE인 기록만 보인다
 * @param likedByMe 조회한 사람이 좋아요를 눌렀는지. 비회원이 조회하면 null이다
 * @param viewedByAuthor 조회한 사람이 작성자이면 true다
 */
public record MemoryView(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        Double latitude,
        Double longitude,
        ApproximateLocation approximate,
        LocalDate visitedDate,
        int nights,
        String content,
        String visibility,
        String status,
        List<MemoryPhoto> images,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        Boolean likedByMe,
        boolean viewedByAuthor) {}
