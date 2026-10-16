package com.moewadeul.spot.infra.detail;

/** 날씨 조회에 쓰는 장소 좌표. lat, lng는 위도와 경도이고, weatherNx와 weatherNy는 장소를 저장할 때 계산해 둔 기상청 격자 좌표다. */
public record SpotWeatherLocationRow(double lat, double lng, int weatherNx, int weatherNy) {}
