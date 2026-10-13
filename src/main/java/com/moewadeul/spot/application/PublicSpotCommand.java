package com.moewadeul.spot.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 공공데이터 원천의 장소 한 건. 외부 ID와 이름을 뺀 문자열 필드는 null이어도 된다.
 *
 * <p>facilities는 원천 항목 이름을 키로, 그 값을 값으로 담는다. null이나 빈 맵을 넘기면 서버는
 * {@code public_spot_detail.facilities}를 NULL로 저장한다.
 *
 * <p>operatingStatus, closedFrom, closedUntil은 원천이 준 운영 상태와 휴장 기간이고, 원천에 없으면 null이다. 서버는 세 값을 그대로 저장할 뿐
 * closedFrom이 closedUntil보다 늦은지 같은 앞뒤 관계를 검사하지 않는다.
 *
 * <p>settings는 야영장의 입지다. null을 넘기면 입지가 없는 것으로 본다. 원천이 입지를 주지 않는 장소는 빈 집합을 넘긴다.
 *
 * <p>sourceDate는 원천 데이터의 기준일이다. 파일로 받는 원천은 파일 기준일을, 고캠핑은 항목별 원천 수정일을 넣고, 기준일이 없으면 null을 넣는다.
 */
public record PublicSpotCommand(
        String externalId,
        String name,
        double latitude,
        double longitude,
        String address,
        String category,
        Map<String, String> facilities,
        String phone,
        String homepage,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        LocalDate sourceDate,
        Set<PublicSpotSetting> settings) {

    public PublicSpotCommand {
        settings = settings == null ? Set.of() : Set.copyOf(settings);
    }
}
