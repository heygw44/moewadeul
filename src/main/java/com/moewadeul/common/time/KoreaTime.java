package com.moewadeul.common.time;

import java.time.ZoneId;

public final class KoreaTime {

    public static final String ZONE_ID = "Asia/Seoul";

    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private KoreaTime() {}
}
