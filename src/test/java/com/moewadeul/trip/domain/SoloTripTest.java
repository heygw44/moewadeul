package com.moewadeul.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SoloTripTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final long MEMBER_ID = 1L;
    private static final long SPOT_ID = 10L;

    @ParameterizedTest(name = "출발 {0}일 뒤, {1}박")
    @CsvSource({"1, 1", "60, 1", "1, 3", "30, 2"})
    @DisplayName("[F-23][ST-02] 출발일이 내일부터 60일 안이고 1~3박이면 일정을 만든다")
    void registersScheduleInRange(int daysUntilStart, int nights) {
        // given
        LocalDate start = TODAY.plusDays(daysUntilStart);

        // when
        SoloTrip trip = register(start, start.plusDays(nights), List.of());

        // then
        assertThat(trip.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(trip.getSpotId()).isEqualTo(SPOT_ID);
        assertThat(trip.getStartDate()).isEqualTo(start);
        assertThat(trip.getEndDate()).isEqualTo(start.plusDays(nights));
        assertThat(trip.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "출발 {0}일 뒤, {1}박")
    @CsvSource({"0, 1", "-1, 1", "61, 1", "1, 0", "1, 4", "1, -1"})
    @DisplayName("[F-23][ST-02] 오늘 이전 출발, 61일 뒤 출발, 0박, 4박이면 SOLO_TRIP_SCHEDULE_INVALID로 거부한다")
    void rejectsScheduleOutOfRange(int daysUntilStart, int nights) {
        // given
        LocalDate start = TODAY.plusDays(daysUntilStart);

        // when, then
        assertRejected(
                () -> register(start, start.plusDays(nights), List.of()), TripErrorCode.SOLO_TRIP_SCHEDULE_INVALID);
    }

    @Test
    @DisplayName("[F-23][ST-02] 출발일이나 종료일이 없으면 SOLO_TRIP_SCHEDULE_INVALID로 거부한다")
    void rejectsMissingDates() {
        assertRejected(() -> register(null, TODAY.plusDays(2), List.of()), TripErrorCode.SOLO_TRIP_SCHEDULE_INVALID);
        assertRejected(() -> register(TODAY.plusDays(1), null, List.of()), TripErrorCode.SOLO_TRIP_SCHEDULE_INVALID);
    }

    @Test
    @DisplayName("[F-23][ST-03] 진행 예정 일정이 이미 3개면 겹치지 않아도 SOLO_TRIP_LIMIT로 거부한다")
    void rejectsFourthUpcomingTrip() {
        // given
        List<SoloTrip> upcoming = List.of(
                trip("2026-10-10", "2026-10-11"), trip("2026-10-12", "2026-10-13"), trip("2026-10-14", "2026-10-15"));

        // when, then
        assertRejected(
                () -> register(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-21"), upcoming),
                TripErrorCode.SOLO_TRIP_LIMIT);
    }

    @ParameterizedTest(name = "{0}~{1}")
    @CsvSource({"2026-11-08, 2026-11-10", "2026-11-06, 2026-11-08", "2026-11-07, 2026-11-09", "2026-11-06, 2026-11-09"})
    @DisplayName("[F-23][ST-03] 11/7~11/9 일정과 밤이 하루라도 겹치면 SOLO_TRIP_OVERLAP으로 거부한다")
    void rejectsOverlappingNights(LocalDate start, LocalDate end) {
        // given
        List<SoloTrip> upcoming = List.of(trip("2026-11-07", "2026-11-09"));

        // when, then
        assertRejected(() -> register(start, end, upcoming), TripErrorCode.SOLO_TRIP_OVERLAP);
    }

    @ParameterizedTest(name = "{0}~{1}")
    @CsvSource({"2026-11-09, 2026-11-10", "2026-11-05, 2026-11-07"})
    @DisplayName("[F-23][ST-03] 앞 일정의 종료일에 출발하거나 출발일에 끝나는 일정은 밤이 겹치지 않아 만든다")
    void allowsAdjacentTrips(LocalDate start, LocalDate end) {
        // given
        List<SoloTrip> upcoming = List.of(trip("2026-11-07", "2026-11-09"));

        // when
        SoloTrip trip = register(start, end, upcoming);

        // then
        assertThat(trip.getStartDate()).isEqualTo(start);
    }

    @Test
    @DisplayName("[F-23][ST-05] 등록한 회원이 아니면 requireRegisteredBy가 ACCESS_DENIED다")
    void requireRegisteredByRejectsOthers() {
        SoloTrip trip = trip("2026-11-07", "2026-11-09");

        assertThatCode(() -> trip.requireRegisteredBy(MEMBER_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> trip.requireRegisteredBy(MEMBER_ID + 1))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    private static SoloTrip trip(String start, String end) {
        return register(LocalDate.parse(start), LocalDate.parse(end), List.of());
    }

    private static SoloTrip register(LocalDate start, LocalDate end, List<SoloTrip> upcoming) {
        return SoloTrip.register(MEMBER_ID, SPOT_ID, new SoloTrip.Schedule(start, end), TODAY, upcoming, NOW);
    }

    private static void assertRejected(Runnable action, TripErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
