package com.moewadeul.spot.application;

/**
 * 공공데이터 원천이 알려 준 야영장의 입지. 서버는 이 값을 {@code public_spot_setting.setting}에 이름 그대로 저장한다.
 *
 * <p>한 야영장에 입지가 여러 개 붙을 수 있고, 원천이 입지를 주지 않은 야영장은 입지가 하나도 없다. 박지와 자연휴양림은 입지를 갖지 않는다.
 */
public enum PublicSpotSetting {
    MOUNTAIN,
    FOREST,
    VALLEY,
    BEACH,
    ISLAND,
    RIVER,
    LAKE,
    CITY
}
