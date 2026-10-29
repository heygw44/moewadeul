package com.moewadeul.spot.application.detail;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LAT;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LNG;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotDetailClosureAndSyncIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private SpotDetailFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SpotDetailFixture(jdbcTemplate, memberFixture);
    }

    @Test
    @DisplayName("[F-05] 휴장 기간이 한국 날짜로 시작되면 휴장으로, 시작 전날이면 휴장이 아닌 것으로 판정한다")
    void judgesClosedNowByKoreanDate() {
        // given
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(
                spotId,
                "GOCAMPING",
                null,
                null,
                "OPERATING",
                LocalDate.parse("2026-11-01"),
                LocalDate.parse("2027-03-31"));

        // when
        clock.setInstant(Instant.parse("2026-10-31T14:30:00Z"));
        SpotDetail beforeClosure = spotDetailQueryService.findDetail(spotId);
        // when
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        SpotDetail duringClosure = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(beforeClosure.publicDetail().closedNow()).isFalse();
        assertThat(duringClosure.publicDetail().closedNow()).isTrue();
        assertThat(duringClosure.publicDetail().operatingStatus()).isEqualTo(PublicSpotOperatingStatus.OPERATING);
        assertThat(duringClosure.publicDetail().closedFrom()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(duringClosure.publicDetail().closedUntil()).isEqualTo(LocalDate.parse("2027-03-31"));
    }

    @Test
    @DisplayName("[F-05] 휴장 기간이 없고 운영 상태가 휴장이면 휴장으로 판정한다")
    void judgesClosedNowByOperatingStatusWithoutPeriod() {
        // given
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "GOCAMPING", null, null, "TEMPORARILY_CLOSED", null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().operatingStatus()).isEqualTo(PublicSpotOperatingStatus.TEMPORARILY_CLOSED);
        assertThat(detail.publicDetail().closedNow()).isTrue();
    }

    @Test
    @DisplayName("[F-05] 점검 시각은 장소 원천과 같은 종류에서 마지막으로 성공한 동기화의 종료 시각이고, 실패와 실행 중인 기록은 무시한다")
    void reportsLatestCompletedSyncFinishTimeOfSameSource() {
        // given
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-01 03:00:00.000000", "2026-10-01 03:10:00.000000");
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-02 03:00:00.000000", "2026-10-02 03:12:30.500000");
        insertSyncRun("GOCAMPING", "FAILED", "2026-10-03 03:00:00.000000", "2026-10-03 03:05:00.000000");
        insertSyncRun("GOCAMPING", "RUNNING", "2026-10-04 03:00:00.000000", null);
        insertSyncRun("FOREST", "COMPLETED", "2026-10-05 03:00:00.000000", "2026-10-05 03:01:00.000000");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().checkedAt()).isEqualTo(Instant.parse("2026-10-02T03:12:30.500Z"));
    }

    @Test
    @DisplayName("[F-05] 성공한 동기화 기록이 없거나 다른 종류뿐이면 점검 시각은 null이다")
    void returnsNullCheckedAtWithoutCompletedRunOfSameSource() {
        // given
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);

        // when, then
        assertThat(spotDetailQueryService.findDetail(spotId).publicDetail().checkedAt())
                .isNull();

        // when, then
        insertSyncRun("FOREST", "COMPLETED", "2026-10-05 03:00:00.000000", "2026-10-05 03:01:00.000000");
        insertSyncRun("GOCAMPING", "FAILED", "2026-10-06 03:00:00.000000", "2026-10-06 03:01:00.000000");
        assertThat(spotDetailQueryService.findDetail(spotId).publicDetail().checkedAt())
                .isNull();
    }

    @Test
    @DisplayName("[F-05] 자연휴양림 상세의 점검 시각은 자연휴양림 동기화 기록을 따른다")
    void forestDetailUsesForestSyncRuns() {
        // given
        long spotId = spotFixture.insert(aSpot().type("FOREST").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "FOREST", null, LocalDate.parse("2025-12-31"), null, null, null);
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-07 03:00:00.000000", "2026-10-07 03:30:00.000000");
        insertSyncRun("FOREST", "COMPLETED", "2026-10-05 03:00:00.000000", "2026-10-05 03:01:00.000000");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().checkedAt()).isEqualTo(Instant.parse("2026-10-05T03:01:00Z"));
    }

    @Test
    @DisplayName("[F-05] 박지 상세에는 공공데이터 상세가 없으므로 동기화 기록이 있어도 점검 시각을 주지 않는다")
    void bakjiHasNoCheckedAt() {
        // given
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, FALSE, FALSE, NOW(6), NOW(6))",
                spotId,
                memberFixture.save(aMember().status(MemberStatus.ACTIVE)).getId());
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-07 03:00:00.000000", "2026-10-07 03:30:00.000000");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail()).isNull();
    }

    private void insertSyncRun(String jobType, String status, String startedAt, String finishedAt) {
        jdbcTemplate.update(
                "INSERT INTO sync_job_run (job_type, status, processed_count, skipped_count, started_at,"
                        + " finished_at, created_at, updated_at)"
                        + " VALUES (?, ?, 0, 0, ?, ?, NOW(6), NOW(6))",
                jobType,
                status,
                startedAt,
                finishedAt);
    }
}
