package com.moewadeul.aisearch.application;

import com.moewadeul.common.time.KoreaTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** AI 검색의 하루는 한국 날짜 기준이다. 사용량 집계와 정리 작업이 같은 경계를 쓰도록 계산을 한곳에 둔다. */
final class AiSearchDay {

    private AiSearchDay() {}

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(KoreaTime.ZONE));
    }

    /** 호출하면 now부터 date 다음 날 0시(한국 시간)까지 남은 시간을 돌려준다. */
    static Duration untilNextDay(Instant now, LocalDate date) {
        return Duration.between(
                now, date.plusDays(1).atStartOfDay(KoreaTime.ZONE).toInstant());
    }
}
