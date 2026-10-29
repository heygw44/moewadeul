package com.moewadeul.spot.application.detail;

import static com.moewadeul.member.domain.MemberBuilder.aMember;

import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.MemberStatus;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

final class SpotDetailFixture {

    static final double RIDGE_LAT = 37.71;
    static final double RIDGE_LNG = 128.75;

    private final JdbcTemplate jdbcTemplate;
    private final MemberFixture memberFixture;

    SpotDetailFixture(JdbcTemplate jdbcTemplate, MemberFixture memberFixture) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberFixture = memberFixture;
    }

    void insertPublicDetail(
            long spotId,
            String source,
            String facilities,
            LocalDate sourceDate,
            String operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil) {
        jdbcTemplate.update(
                "INSERT INTO public_spot_detail"
                        + " (spot_id, source, external_id, facilities, source_date, operating_status, closed_from,"
                        + " closed_until, synced_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))",
                spotId,
                source,
                String.valueOf(spotId),
                facilities,
                sourceDate,
                operatingStatus,
                closedFrom,
                closedUntil);
    }

    long insertBasecamp(long spotId, String status, String startDate, String endDate) {
        return insertBasecamp(spotId, status, startDate, endDate, 6, TestSequence.unique("베이스캠프"));
    }

    long insertBasecamp(long spotId, String status, String startDate, String endDate, int capacity, String title) {
        long leaderId =
                memberFixture.save(aMember().status(MemberStatus.ACTIVE)).getId();
        jdbcTemplate.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, ?, '설명', ?, ?, ?, ?, NOW(6), NOW(6))",
                leaderId,
                spotId,
                title,
                startDate,
                endDate,
                capacity,
                status);
        return jdbcTemplate.queryForObject("SELECT id FROM basecamp WHERE title = ?", Long.class, title);
    }

    void insertBasecampMembers(long basecampId, String status, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at,"
                            + " updated_at) VALUES (?, ?, 'MEMBER', ?, NOW(6), NOW(6), NOW(6))",
                    basecampId,
                    memberFixture.save(aMember().status(MemberStatus.ACTIVE)).getId(),
                    status);
        }
    }
}
