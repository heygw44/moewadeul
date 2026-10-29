package com.moewadeul.spot.application.detail;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LAT;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LNG;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.common.testsupport.TestSequence;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotDetailExpectedPeopleIntegrationTest {

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
        fixture = new SpotDetailFixture(jdbcTemplate);
    }

    @Test
    @DisplayName("[F-05] 겹치는 확정 베이스캠프는 밤마다 인원을 합산하고, 종료일은 밤으로 세지 않는다")
    void sumsConfirmedMembersPerNightAndExcludesEndDate() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long first = fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-09");
        fixture.insertBasecampMembers(first, "ACTIVE", 3);
        long second = fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-08", "2026-11-09");
        fixture.insertBasecampMembers(second, "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 3),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 5));
    }

    @Test
    @DisplayName("[F-05] 탈퇴하거나 강퇴된 멤버는 예상 인원에 세지 않는다")
    void doesNotCountLeftOrKickedMembers() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long basecampId = fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-08");
        fixture.insertBasecampMembers(basecampId, "ACTIVE", 2);
        fixture.insertBasecampMembers(basecampId, "LEFT", 1);
        fixture.insertBasecampMembers(basecampId, "KICKED", 1);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 2));
    }

    @Test
    @DisplayName("[F-05] 확정이 아닌 베이스캠프와 다른 장소의 베이스캠프는 예상 인원에 세지 않는다")
    void countsOnlyConfirmedBasecampsOfThisSpot() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long otherSpotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        for (String status : new String[] {"RECRUITING", "CLOSED", "CANCELED", "COMPLETED"}) {
            fixture.insertBasecampMembers(
                    fixture.insertBasecamp(spotId, status, "2026-11-07", "2026-11-08"), "ACTIVE", 2);
        }
        fixture.insertBasecampMembers(
                fixture.insertBasecamp(otherSpotId, "CONFIRMED", "2026-11-07", "2026-11-08"), "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).isEmpty();
    }

    @Test
    @DisplayName("[F-05] 이미 지난 밤은 예상 인원에서 빼고, 오늘 밤부터 센다")
    void excludesPastNights() {
        // given
        clock.setInstant(Instant.parse("2026-11-08T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertBasecampMembers(
                fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-10"), "ACTIVE", 2);
        fixture.insertBasecampMembers(
                fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-06", "2026-11-08"), "ACTIVE", 4);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 2),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-09"), 2));
    }

    @Test
    @DisplayName("[F-23][ST-05] 개인 일정은 밤마다 1명으로 예상 인원에 더하고, 같은 밤의 확정 베이스캠프 인원과 합산한다")
    void addsSoloTripsToExpectedPeople() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        insertSoloTrip(fixture.insertMember(TestSequence.nickname()), spotId, "2026-11-07", "2026-11-09");
        fixture.insertBasecampMembers(
                fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-08", "2026-11-09"), "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 1),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 3));
    }

    @Test
    @DisplayName("[F-23][ST-05] 탈퇴한 회원의 개인 일정, 다른 장소의 일정, 이미 끝난 일정은 예상 인원에 세지 않는다")
    void excludesWithdrawnOtherSpotAndFinishedSoloTrips() {
        // given
        clock.setInstant(Instant.parse("2026-11-08T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long otherSpotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long withdrawnId = fixture.insertMember(TestSequence.nickname());
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", withdrawnId);
        insertSoloTrip(withdrawnId, spotId, "2026-11-09", "2026-11-10");
        insertSoloTrip(fixture.insertMember(TestSequence.nickname()), otherSpotId, "2026-11-09", "2026-11-10");
        insertSoloTrip(fixture.insertMember(TestSequence.nickname()), spotId, "2026-11-06", "2026-11-08");
        insertSoloTrip(fixture.insertMember(TestSequence.nickname()), spotId, "2026-11-07", "2026-11-09");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 1));
    }

    @Test
    @DisplayName("[F-05] 예상 인원의 오늘은 UTC가 아니라 한국 날짜로 정한다")
    void decidesTodayByKoreanDate() {
        // given
        clock.setInstant(Instant.parse("2026-11-07T15:30:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertBasecampMembers(
                fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-09"), "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 2));
    }

    @Test
    @DisplayName("[F-05] 숙박 행사의 확정 참가자를 예상 인원에 더하고, 같은 밤의 확정 베이스캠프 인원과 합산한다")
    void addsConfirmedProgramParticipantsToExpectedPeople() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long programId = insertProgram(spotId, "2026-11-07T01:00:00Z", "2026-11-08T03:00:00Z", true, "SCHEDULED");
        insertProgramApplications(programId, "CONFIRMED", 2);
        insertProgramApplications(programId, "PENDING_PAYMENT", 1);
        insertProgramApplications(programId, "CANCELED", 1);
        insertProgramApplications(programId, "EXPIRED", 1);
        fixture.insertBasecampMembers(
                fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-09"), "ACTIVE", 3);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 5),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 3));
    }

    @Test
    @DisplayName("[F-05] 행사의 야영 밤은 시작·종료 시각의 UTC 날짜가 아니라 한국 날짜로 정한다")
    void countsProgramNightsByKoreanDate() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long programId = insertProgram(spotId, "2026-11-06T16:00:00Z", "2026-11-07T16:00:00Z", true, "SCHEDULED");
        insertProgramApplications(programId, "CONFIRMED", 4);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 4));
    }

    @Test
    @DisplayName("[F-05] 당일 행사, 취소된 행사, 다른 장소의 행사, 끝난 행사의 참가자는 예상 인원에 세지 않는다")
    void excludesDayCanceledOtherSpotAndPastPrograms() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long otherSpotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        String start = "2026-11-07T01:00:00Z";
        String end = "2026-11-08T03:00:00Z";
        insertProgramApplications(insertProgram(spotId, start, end, false, "SCHEDULED"), "CONFIRMED", 2);
        insertProgramApplications(insertProgram(spotId, start, end, true, "CANCELED"), "CONFIRMED", 2);
        insertProgramApplications(insertProgram(otherSpotId, start, end, true, "SCHEDULED"), "CONFIRMED", 2);
        insertProgramApplications(
                insertProgram(spotId, "2026-10-30T01:00:00Z", "2026-10-31T03:00:00Z", true, "SCHEDULED"),
                "CONFIRMED",
                2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).isEmpty();
    }

    private void insertSoloTrip(long memberId, long spotId, String startDate, String endDate) {
        jdbcTemplate.update(
                "INSERT INTO solo_trip (member_id, spot_id, start_date, end_date, created_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6))",
                memberId,
                spotId,
                startDate,
                endDate);
    }

    private long insertProgram(long spotId, String startAt, String endAt, boolean overnight, String status) {
        String title = TestSequence.unique("행사");
        jdbcTemplate.update(
                "INSERT INTO program (title, description, spot_id, location_text, start_at, end_at, capacity, fee,"
                        + " apply_open_at, apply_close_at, payment_deadline_minutes, overnight, status, created_by,"
                        + " created_at, updated_at) VALUES (?, '설명', ?, '입구', ?, ?, 20, 30000, '2026-10-01 00:00:00',"
                        + " '2026-10-20 00:00:00', 15, ?, ?, ?, NOW(6), NOW(6))",
                title,
                spotId,
                toUtcDateTime(startAt),
                toUtcDateTime(endAt),
                overnight,
                status,
                fixture.insertMember(TestSequence.nickname()));
        return jdbcTemplate.queryForObject("SELECT id FROM program WHERE title = ?", Long.class, title);
    }

    private void insertProgramApplications(long programId, String status, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at,"
                            + " updated_at) VALUES (?, ?, ?, '2026-10-31 00:00:00', NOW(6), NOW(6))",
                    programId,
                    fixture.insertMember(TestSequence.nickname()),
                    status);
        }
    }

    private static String toUtcDateTime(String isoInstant) {
        return Instant.parse(isoInstant).toString().replace("T", " ").replace("Z", "");
    }
}
