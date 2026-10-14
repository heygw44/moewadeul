package com.moewadeul.aisearch.application;

import java.util.List;
import java.util.Set;

/**
 * 문장 하나에서 읽어 낸 장소 검색 조건. 조건이 없는 항목은 null, false, 빈 목록이다.
 *
 * @param region 지역 이름(시·도, 시군구, 읍면동, 산 이름 등). 문장에 쓴 그대로이고, 없으면 null
 * @param radiusKm 문장에 거리가 적혀 있을 때만 채운다. 50km를 넘으면 50으로 줄인다.
 * @param types 장소 유형
 * @param hasWater 식수가 있는 곳을 원하는지
 * @param hasToilet 화장실이 있는 곳을 원하는지
 * @param excludeWarning 공원 경계 경고가 붙은 곳을 빼고 싶은지
 * @param hideClosed 휴장 중인 곳을 빼고 싶은지
 * @param settings 입지
 * @param unparsed 조건으로 바꾸지 못한 표현(날짜, 분위기 등). 화면이 "반영하지 못한 조건"으로 보여 준다.
 */
public record SpotQueryConditions(
        String region,
        Double radiusKm,
        Set<AiSpotType> types,
        boolean hasWater,
        boolean hasToilet,
        boolean excludeWarning,
        boolean hideClosed,
        Set<AiSpotSetting> settings,
        List<String> unparsed) {

    /** 지역 검색의 반경 상한(km). 이보다 큰 값은 이 값으로 줄인다. */
    public static final double MAX_RADIUS_KM = 50;

    public SpotQueryConditions {
        if (region != null && region.isBlank()) {
            region = null;
        }
        if (radiusKm != null && radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be positive");
        }
        if (radiusKm != null && radiusKm > MAX_RADIUS_KM) {
            radiusKm = MAX_RADIUS_KM;
        }
        types = types == null ? Set.of() : Set.copyOf(types);
        settings = settings == null ? Set.of() : Set.copyOf(settings);
        unparsed = unparsed == null ? List.of() : List.copyOf(unparsed);
    }
}
