package com.moewadeul.memory.domain.report;

import java.time.Instant;

public interface MemoryReportRepository {

    MemoryReport saveAndFlush(MemoryReport report);

    boolean existsByMemoryIdAndReporterId(long memoryId, long reporterId);

    long countByMemoryIdAndReviewedAtIsNull(long memoryId);

    int markReviewed(long memoryId, Instant now);
}
