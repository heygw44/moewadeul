package com.moewadeul.spot.application.detail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 한 곳의 상세를 찾는다. 날씨는 붙이지 않는다.
 *
 * <p>상세는 DB 값만 읽어서 외부 API를 기다리지 않는다. 그래서 화면은 공원 경계 경고와 출처·기준일 같은 장소 근거를 날씨보다 먼저 그린다. 날씨는
 * {@link SpotWeatherQueryService}가 따로 준다.
 */
@Service
@RequiredArgsConstructor
public class SpotDetailQueryService {

    private final SpotDetailReader spotDetailReader;

    /**
     * 호출하면 spotId인 장소의 상세를 돌려준다.
     *
     * <p>장소가 없거나 숨김, 삭제, 검토 대기처럼 ACTIVE가 아니면 서비스는 NOT_FOUND로 거부한다.
     */
    public SpotDetail findDetail(long spotId) {
        return spotDetailReader.read(spotId);
    }
}
