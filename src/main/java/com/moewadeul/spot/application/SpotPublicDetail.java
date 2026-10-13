package com.moewadeul.spot.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 야영장과 자연휴양림의 상세. 값은 공공데이터 원천이 준 그대로이고, 원천에 없으면 {@code null}이다.
 *
 * <p>sourceDate는 원천 데이터의 기준일이다. 자연휴양림은 파일 기준일이고, 고캠핑 야영장은 항목별 원천 수정일이다. 원천이 주지 않으면 {@code null}이다. checkedAt은 그 원천을 마지막으로 성공 동기화한 시각이고, 성공한 실행이 없으면 {@code null}이다. facilities는 시설 정보가
 * 하나도 없으면 {@code null}이다.
 *
 * <p>closedNow는 조회한 날의 한국 날짜에 휴장 중인지를 뜻한다. 서비스는 휴장 기간이 있으면 기간을 보고, 기간이 없을 때만 운영 상태를 본다.
 */
public record SpotPublicDetail(
        PublicSpotSource source,
        String category,
        SpotFacilities facilities,
        String phone,
        String homepage,
        LocalDate sourceDate,
        Instant checkedAt,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        boolean closedNow) {}
