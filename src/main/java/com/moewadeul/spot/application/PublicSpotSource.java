package com.moewadeul.spot.application;

/**
 * 공공데이터 장소의 출처. 서버는 이 이름을 {@code public_spot_detail.source}에 그대로 저장한다.
 *
 * <p>출처마다 장소 종류가 하나로 정해져 있다. 고캠핑 야영장은 {@code CAMPSITE}, 자연휴양림은 {@code FOREST}로 저장한다.
 * 전국휴양림표준데이터도 장소 종류는 자연휴양림({@code FOREST})이지만, 국립 파일과 외부 ID 체계가 달라서 출처를 따로 둔다.
 */
public enum PublicSpotSource {
    GOCAMPING("CAMPSITE"),
    FOREST("FOREST"),
    FOREST_STANDARD("FOREST");

    private final String spotType;

    PublicSpotSource(String spotType) {
        this.spotType = spotType;
    }

    String spotType() {
        return spotType;
    }
}
