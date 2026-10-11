package com.moewadeul.basecamp.application;

/**
 * 베이스캠프 검색에서 장소 유형 조건으로 받는 값이다. 장소 모듈의 유형 이름(spot.type 열의 값)과 같다.
 *
 * <p>모듈 규칙상 베이스캠프 모듈은 장소 모듈의 domain 클래스를 쓰지 않는다. 그래서 검색 조건에 필요한 이름만 여기에 따로 둔다.
 */
public enum BasecampSpotType {
    CAMPSITE,
    FOREST,
    BAKJI
}
