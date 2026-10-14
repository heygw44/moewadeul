package com.moewadeul.common.time;

import java.time.ZoneId;

/** 정책상 날짜와 0시는 한국 시간 기준이다. 서버는 한국 시간대가 필요한 곳에서 이 상수를 쓴다. */
public final class KoreaTime {

    /** {@code @Scheduled(zone = ...)}처럼 컴파일 상수가 필요한 곳에 쓰는 시간대 이름. */
    public static final String ZONE_ID = "Asia/Seoul";

    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private KoreaTime() {}
}
