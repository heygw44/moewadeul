package com.moewadeul.spot.infra;

/** 공공데이터 장소 한 건의 위치 조회 결과. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다. */
public record PublicSpotLocationRow(String externalId, String name, double latitude, double longitude) {}
