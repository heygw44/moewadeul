package com.moewadeul.memory.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 기록 검토 목록의 한 항목. 관리자에게도 정확한 좌표는 주지 않으므로 대략 좌표만 담는다. 신고 수와 최근 신고는 검토 전 신고만 담고 신고자는
 * 담지 않는다.
 *
 * @param approxLat 위도를 소수 둘째 자리로 반올림한 값이다
 * @param approxLng 경도를 소수 둘째 자리로 반올림한 값이다
 * @param content 글이 없으면 null이다
 * @param images 기록 안 순서대로 모든 사진이다
 * @param statusChangedAt 기록이 마지막으로 바뀐 시각이다. 작성자가 고쳐도 바뀐다
 */
public record AdminMemorySummary(
        long memoryId,
        long authorId,
        String authorNickname,
        double approxLat,
        double approxLng,
        LocalDate visitedDate,
        int nights,
        String content,
        List<MemoryPhoto> images,
        String status,
        long reportCount,
        ReasonCounts reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {

    /** 검토 전 신고의 사유별 수. */
    public record ReasonCounts(long spam, long abuse, long illegalCamping, long privacy, long moneyScam, long other) {}

    /** 검토 전 신고 한 건. content는 신고 내용이 없으면 null이다. */
    public record RecentReport(String reason, String content, Instant createdAt) {}
}
