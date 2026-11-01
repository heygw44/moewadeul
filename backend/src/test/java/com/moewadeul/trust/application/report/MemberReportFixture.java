package com.moewadeul.trust.application.report;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

public final class MemberReportFixture {

    private final JdbcTemplate jdbc;

    public MemberReportFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertMemberReport(
            long reporterId, long targetId, long basecampId, String type, String status, Instant createdAt) {
        return insert(reporterId, targetId, basecampId, "MEMBER", null, type, status, createdAt);
    }

    public long insertReviewReport(
            long reporterId, long targetId, long basecampId, long reviewId, String status, Instant createdAt) {
        return insert(reporterId, targetId, basecampId, "REVIEW", reviewId, "INAPPROPRIATE_REVIEW", status, createdAt);
    }

    private long insert(
            long reporterId,
            long targetId,
            long basecampId,
            String kind,
            Long reviewId,
            String type,
            String status,
            Instant createdAt) {
        LocalDateTime at = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO member_report (reporter_id, target_member_id, basecamp_id, kind, companion_review_id,"
                        + " type, content, urgent, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reporterId,
                targetId,
                basecampId,
                kind,
                reviewId,
                type,
                "신고 내용 " + type,
                "HARASSMENT_OR_THREAT".equals(type),
                status,
                at,
                at);
        return jdbc.queryForObject("SELECT MAX(id) FROM member_report", Long.class);
    }
}
