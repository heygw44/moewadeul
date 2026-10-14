package com.moewadeul.memory.infra.moderation;

import java.time.Instant;

/** 관리자 기록 검토 목록에 붙일 검토 전 신고 한 건. 신고자는 담지 않는다. content는 신고 내용이 없으면 null이다. */
public record AdminMemoryRecentReportRow(long memoryId, String reason, String content, Instant createdAt) {}
