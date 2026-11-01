package com.moewadeul.trust.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;

import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CompanionReviewFixture {

    private static final String AT = "2026-09-01 03:00:00";

    private final JdbcTemplate jdbc;
    private final MemberJpaRepository memberRepository;

    public CompanionReviewFixture(JdbcTemplate jdbc, MemberJpaRepository memberRepository) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
    }

    public long saveMember(String nickname) {
        return memberRepository
                .saveAndFlush(aMember().nickname(nickname).build())
                .getId();
    }

    public long saveVerifiedMember(String nickname) {
        long memberId = saveMember(nickname);
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT,
                AT,
                AT);
        return memberId;
    }

    public long saveBasecamp(String status, Instant completedAt) {
        long leaderId = saveVerifiedMember(TestSequence.nickname());
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        long spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " completed_at, created_at, updated_at)"
                        + " VALUES (?, ?, '굴업도 주말 1박', '함께 가요', '2026-09-20', '2026-09-22', 4, ?, ?, ?, ?)",
                leaderId,
                spotId,
                status,
                completedAt == null ? null : LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC),
                AT,
                AT);
        long basecampId = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMember(basecampId, leaderId, "LEADER", "ACTIVE");
        return basecampId;
    }

    public void insertQualifiedRecord(long memberId, Instant completedAt) {
        long[] basecamps = {
            saveBasecamp("COMPLETED", completedAt),
            saveBasecamp("COMPLETED", completedAt),
            saveBasecamp("COMPLETED", completedAt)
        };
        for (long basecampId : basecamps) {
            insertMember(basecampId, memberId, "MEMBER", "ACTIVE");
        }
        insertReview(basecamps[0], leaderIdOf(basecamps[0]), memberId, true, completedAt);
        insertReview(basecamps[0], saveVerifiedMember(TestSequence.nickname()), memberId, true, completedAt);
        insertReview(basecamps[1], leaderIdOf(basecamps[1]), memberId, true, completedAt);
        insertReview(basecamps[1], saveVerifiedMember(TestSequence.nickname()), memberId, true, completedAt);
        insertReview(basecamps[2], leaderIdOf(basecamps[2]), memberId, false, completedAt);
    }

    public long leaderIdOf(long basecampId) {
        return jdbc.queryForObject("SELECT leader_id FROM basecamp WHERE id = ?", Long.class, basecampId);
    }

    public void insertMember(long basecampId, long memberId, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, left_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                basecampId,
                memberId,
                role,
                status,
                AT,
                "ACTIVE".equals(status) ? null : AT,
                AT,
                AT);
    }

    public void insertApplication(long basecampId, long applicantId, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, applied_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                basecampId,
                applicantId,
                status,
                AT,
                AT,
                AT);
    }

    public void insertEarlyLeaver(long basecampId, long memberId, Instant leftAt) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, left_at, early_leave,"
                        + " created_at, updated_at) VALUES (?, ?, 'MEMBER', 'LEFT', ?, ?, TRUE, ?, ?)",
                basecampId,
                memberId,
                AT,
                LocalDateTime.ofInstant(leftAt, ZoneOffset.UTC),
                AT,
                AT);
    }

    public long insertReview(
            long basecampId, long reviewerId, long revieweeId, boolean rejoinWanted, Instant createdAt) {
        jdbc.update(
                "INSERT INTO companion_review (basecamp_id, reviewer_id, reviewee_id, rejoin_wanted, comment,"
                        + " created_at) VALUES (?, ?, ?, ?, ?, ?)",
                basecampId,
                reviewerId,
                revieweeId,
                rejoinWanted,
                "코멘트 " + reviewerId + "→" + revieweeId,
                LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return jdbc.queryForObject("SELECT MAX(id) FROM companion_review", Long.class);
    }

    public void insertTag(long reviewId, String tag) {
        jdbc.update("INSERT INTO companion_review_tag (review_id, tag) VALUES (?, ?)", reviewId, tag);
    }

    public void hide(long reviewId) {
        jdbc.update("UPDATE companion_review SET hidden_at = ? WHERE id = ?", AT, reviewId);
    }

    public int reviewCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM companion_review", Integer.class);
    }
}
