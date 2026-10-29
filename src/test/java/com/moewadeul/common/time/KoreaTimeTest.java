package com.moewadeul.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreaTimeTest {

    private static final Instant JUST_BEFORE_KST_MIDNIGHT = Instant.parse("2026-10-29T14:59:59Z");
    private static final Instant KST_MIDNIGHT = Instant.parse("2026-10-29T15:00:00Z");

    @Test
    @DisplayName("dateOf는 UTC 15시를 기준으로 한국의 다음 날로 넘어간다")
    void dateOfRollsOverAtKoreanMidnight() {
        assertThat(KoreaTime.dateOf(JUST_BEFORE_KST_MIDNIGHT)).isEqualTo(LocalDate.parse("2026-10-29"));
        assertThat(KoreaTime.dateOf(KST_MIDNIGHT)).isEqualTo(LocalDate.parse("2026-10-30"));
    }

    @Test
    @DisplayName("today는 시계의 존이 UTC여도 한국 날짜를 돌려준다")
    void todayIgnoresClockZone() {
        Clock utcClock = Clock.fixed(KST_MIDNIGHT, ZoneOffset.UTC);

        assertThat(KoreaTime.today(utcClock)).isEqualTo(LocalDate.parse("2026-10-30"));
    }

    @Test
    @DisplayName("today는 시계가 움직이면 그 시각을 따라간다")
    void todayFollowsClock() {
        MutableClock clock = MutableClock.at(JUST_BEFORE_KST_MIDNIGHT);
        assertThat(KoreaTime.today(clock)).isEqualTo(LocalDate.parse("2026-10-29"));

        clock.setInstant(KST_MIDNIGHT);

        assertThat(KoreaTime.today(clock)).isEqualTo(LocalDate.parse("2026-10-30"));
    }
}
