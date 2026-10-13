package com.moewadeul.memory.infra.moderation;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 관리자 기록 검토 목록의 한 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>신고 수 일곱 개는 검토 전 신고만 센 값이다. latitude와 longitude는 정확한 좌표라서 서비스가 대략 좌표로 바꾼 뒤에만 내보낸다. content는 글이
 * 없으면 null이다.
 */
public record AdminMemoryRow(
        long memoryId,
        long authorId,
        String authorNickname,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String content,
        String status,
        long reportCount,
        long spamCount,
        long abuseCount,
        long illegalCampingCount,
        long privacyCount,
        long moneyScamCount,
        long otherCount,
        Instant statusChangedAt) {}
