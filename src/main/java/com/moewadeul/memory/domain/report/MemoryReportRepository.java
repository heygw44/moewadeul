package com.moewadeul.memory.domain.report;

import java.time.Instant;

public interface MemoryReportRepository {

    MemoryReport saveAndFlush(MemoryReport report);

    boolean existsByMemoryIdAndReporterId(long memoryId, long reporterId);

    /** 검토 전(reviewedAt이 null인) 신고의 수를 센다. */
    long countByMemoryIdAndReviewedAtIsNull(long memoryId);

    /**
     * 호출하면 그 기록의 검토 전 신고를 모두 now에 검토를 마친 것으로 표시하고 표시한 건수를 돌려준다. 호출하는 쪽이 같은 트랜잭션에서 바꾼 기록
     * 엔티티가 있으면 먼저 플러시한 뒤 실행한다.
     */
    int markReviewed(long memoryId, Instant now);
}
