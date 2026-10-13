package com.moewadeul.spot.application;

/**
 * 이미 저장한 공공데이터 장소의 위치. 다른 원천과 겹치는지 가리거나 외부 ID를 이어 붙일 때 쓴다.
 *
 * @param externalId 원천이 준 외부 ID
 * @param name 장소 이름
 * @param latitude 위도
 * @param longitude 경도
 */
public record PublicSpotLocation(String externalId, String name, double latitude, double longitude) {}
