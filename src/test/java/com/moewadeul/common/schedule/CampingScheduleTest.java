package com.moewadeul.common.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CampingScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @ParameterizedTest(name = "출발 {0}일 뒤, {1}박 -> {2}")
    @CsvSource({
        "1, 1, true",
        "60, 1, true",
        "30, 3, true",
        "0, 1, false",
        "-1, 1, false",
        "61, 1, false",
        "10, 0, false",
        "10, -1, false",
        "10, 4, false"
    })
    @DisplayName("출발일이 내일부터 60일 안이고 1~3박이면 예약할 수 있는 일정이다")
    void bookableBounds(int daysUntilStart, int nights, boolean expected) {
        LocalDate start = TODAY.plusDays(daysUntilStart);

        assertThat(new CampingSchedule(start, start.plusDays(nights)).isBookableOn(TODAY))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("출발일이나 종료일이 null이면 예약할 수 없는 일정이다")
    void nullDatesAreNotBookable() {
        assertThat(new CampingSchedule(null, TODAY.plusDays(3)).isBookableOn(TODAY))
                .isFalse();
        assertThat(new CampingSchedule(TODAY.plusDays(2), null).isBookableOn(TODAY))
                .isFalse();
    }

    @Test
    @DisplayName("박 수는 종료일에서 출발일을 뺀 날수다")
    void nightsIsDaysBetween() {
        assertThat(new CampingSchedule(TODAY, TODAY.plusDays(3)).nights()).isEqualTo(3);
    }

    @Test
    @DisplayName("날짜 겹침은 양 끝을 포함해서 하루라도 같으면 겹친다")
    void sharesDateIncludesBothEnds() {
        CampingSchedule base = schedule(20, 22);

        assertThat(base.sharesDateWith(schedule(22, 24))).isTrue();
        assertThat(base.sharesDateWith(schedule(18, 20))).isTrue();
        assertThat(base.sharesDateWith(schedule(21, 21))).isTrue();
        assertThat(base.sharesDateWith(schedule(23, 25))).isFalse();
        assertThat(base.sharesDateWith(schedule(17, 19))).isFalse();
    }

    @Test
    @DisplayName("밤 겹침은 맞닿은 일정을 겹치지 않는 것으로 본다")
    void sharesNightExcludesBackToBack() {
        CampingSchedule base = schedule(20, 22);

        assertThat(base.sharesNightWith(schedule(22, 24))).isFalse();
        assertThat(base.sharesNightWith(schedule(18, 20))).isFalse();
        assertThat(base.sharesNightWith(schedule(21, 23))).isTrue();
        assertThat(base.sharesNightWith(schedule(19, 21))).isTrue();
        assertThat(base.sharesNightWith(schedule(20, 22))).isTrue();
    }

    @Test
    @DisplayName("끝날과 시작날이 같은 일정은 날짜는 겹치지만 밤은 겹치지 않는다")
    void backToBackSharesDateButNotNight() {
        CampingSchedule first = schedule(20, 22);
        CampingSchedule second = schedule(22, 24);

        assertThat(first.sharesDateWith(second)).isTrue();
        assertThat(second.sharesDateWith(first)).isTrue();
        assertThat(first.sharesNightWith(second)).isFalse();
        assertThat(second.sharesNightWith(first)).isFalse();
    }

    private static CampingSchedule schedule(int startDay, int endDay) {
        return new CampingSchedule(LocalDate.of(2026, 10, startDay), LocalDate.of(2026, 10, endDay));
    }
}
