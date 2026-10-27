package com.moewadeul.trip.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.ErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import com.moewadeul.trip.domain.TripErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SoloTripServiceIntegrationTest {

    @Autowired
    private SoloTripCommandService commandService;

    @Autowired
    private SoloTripQueryService queryService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-23][ST-01] 일정을 등록하면 행이 저장되고 내 일정 목록에 장소 이름과 함께 보인다")
    void registeredTripAppearsInMyList() {
        // given
        long memberId = saveMember();
        long spotId = saveSpot("능선 끝 평지", false);

        // when
        long tripId = commandService.register(memberId, command(spotId, "2026-11-07", "2026-11-09"));

        // then
        assertThat(queryService.listUpcoming(memberId))
                .containsExactly(new SoloTripItem(
                        tripId,
                        spotId,
                        "능선 끝 평지",
                        "BAKJI",
                        LocalDate.parse("2026-11-07"),
                        LocalDate.parse("2026-11-09")));
    }

    @Test
    @DisplayName("[F-23][ST-04] 공원 경계 경고 박지에도 일정을 등록할 수 있다")
    void registersOnWarningBakji() {
        // given
        long memberId = saveMember();
        long spotId = saveSpot("공원 안 박지", true);

        // when
        long tripId = commandService.register(memberId, command(spotId, "2026-11-07", "2026-11-08"));

        // then
        assertThat(jdbc.queryForObject("SELECT spot_id FROM solo_trip WHERE id = ?", Long.class, tripId))
                .isEqualTo(spotId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "PENDING_REVIEW", "DELETED"})
    @DisplayName("[F-23][ST-04] 숨김·검토 대기·삭제된 장소에는 NOT_FOUND로 거부한다")
    void rejectsInactiveSpot(String status) {
        // given
        long memberId = saveMember();
        long spotId = saveSpot("능선 끝 평지", false);
        jdbc.update("UPDATE spot SET status = ? WHERE id = ?", status, spotId);

        // when, then
        assertRejected(
                () -> commandService.register(memberId, command(spotId, "2026-11-07", "2026-11-08")),
                CommonErrorCode.NOT_FOUND);
        assertThat(tripCount(memberId)).isZero();
    }

    @Test
    @DisplayName("[F-23][ST-03] 진행 예정 일정이 3개면 4번째는 SOLO_TRIP_LIMIT이고, 종료일이 지난 일정은 세지 않는다")
    void limitCountsOnlyUpcomingTrips() {
        // given
        long memberId = saveMember();
        long spotId = saveSpot("능선 끝 평지", false);
        commandService.register(memberId, command(spotId, "2026-10-06", "2026-10-07"));
        commandService.register(memberId, command(spotId, "2026-10-10", "2026-10-11"));
        commandService.register(memberId, command(spotId, "2026-10-12", "2026-10-13"));

        // when, then
        assertRejected(
                () -> commandService.register(memberId, command(spotId, "2026-10-20", "2026-10-21")),
                TripErrorCode.SOLO_TRIP_LIMIT);

        // when
        clock.setInstant(Instant.parse("2026-10-07T03:00:00Z"));
        commandService.register(memberId, command(spotId, "2026-10-20", "2026-10-21"));

        // then
        assertThat(queryService.listUpcoming(memberId)).hasSize(3);
        assertThat(tripCount(memberId)).isEqualTo(4);
    }

    @Test
    @DisplayName("[F-23][ST-03] 내 다른 일정과 밤이 겹치면 SOLO_TRIP_OVERLAP이고, 다른 회원의 일정과는 겹쳐도 된다")
    void overlapIsCheckedPerMember() {
        // given
        long memberId = saveMember();
        long otherId = saveMember();
        long spotId = saveSpot("능선 끝 평지", false);
        commandService.register(memberId, command(spotId, "2026-11-07", "2026-11-09"));

        // when, then
        assertRejected(
                () -> commandService.register(memberId, command(spotId, "2026-11-08", "2026-11-10")),
                TripErrorCode.SOLO_TRIP_OVERLAP);
        commandService.register(memberId, command(spotId, "2026-11-09", "2026-11-10"));
        commandService.register(otherId, command(spotId, "2026-11-08", "2026-11-10"));
        assertThat(tripCount(memberId)).isEqualTo(2);
        assertThat(tripCount(otherId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-23][ST-05] 내 일정 목록은 출발일 순서이고, 끝난 일정·다른 회원의 일정·숨긴 장소의 일정 구분 없이 내 진행 예정 일정만 준다")
    void myListIsOrderedAndOnlyMine() {
        // given
        long memberId = saveMember();
        long otherId = saveMember();
        long spotId = saveSpot("능선 끝 평지", false);
        long hiddenSpotId = saveSpot("숨긴 박지", false);
        long past = commandService.register(memberId, command(spotId, "2026-10-06", "2026-10-07"));
        long later = commandService.register(memberId, command(spotId, "2026-11-20", "2026-11-21"));
        long sooner = commandService.register(memberId, command(hiddenSpotId, "2026-10-20", "2026-10-21"));
        commandService.register(otherId, command(spotId, "2026-10-15", "2026-10-16"));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenSpotId);
        clock.setInstant(Instant.parse("2026-10-07T03:00:00Z"));

        // when
        List<SoloTripItem> items = queryService.listUpcoming(memberId);

        // then
        assertThat(items).extracting(SoloTripItem::soloTripId).containsExactly(sooner, later);
        assertThat(items).extracting(SoloTripItem::soloTripId).doesNotContain(past);
    }

    @Test
    @DisplayName("[F-23][ST-05] 등록한 회원은 일정을 지우고, 다른 회원은 ACCESS_DENIED, 없는 일정은 NOT_FOUND다")
    void deleteChecksOwner() {
        // given
        long memberId = saveMember();
        long otherId = saveMember();
        long spotId = saveSpot("능선 끝 평지", false);
        long tripId = commandService.register(memberId, command(spotId, "2026-11-07", "2026-11-08"));

        // when, then
        assertRejected(() -> commandService.delete(otherId, tripId), CommonErrorCode.ACCESS_DENIED);
        assertThat(tripCount(memberId)).isEqualTo(1);
        commandService.delete(memberId, tripId);
        assertThat(tripCount(memberId)).isZero();
        assertRejected(() -> commandService.delete(memberId, tripId), CommonErrorCode.NOT_FOUND);
    }

    private static SoloTripRegisterCommand command(long spotId, String start, String end) {
        return new SoloTripRegisterCommand(spotId, LocalDate.parse(start), LocalDate.parse(end));
    }

    private long saveSpot(String name, boolean warning) {
        ParkAreaJudgement judgement = warning
                ? ParkAreaJudgement.inside(insertProtectedArea(), MutableClock.DEFAULT_INSTANT)
                : ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT);
        Spot spot = Spot.bakji(name, new GeoPoint(37.25, 127.25), judgement, MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private long insertProtectedArea() {
        jdbc.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES ('설악산', 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText('MULTIPOLYGON(((128 37, 129 37, 129 38, 128 38, 128 37)))', 4326,"
                        + " 'axis-order=long-lat'), NOW(6), NOW(6))");
        return jdbc.queryForObject("SELECT MAX(id) FROM protected_area", Long.class);
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private int tripCount(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM solo_trip WHERE member_id = ?", Integer.class, memberId);
    }

    private static void assertRejected(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
