package com.moewadeul.spot.api.detail;

import com.moewadeul.spot.application.detail.SpotDetailQueryService;
import com.moewadeul.spot.application.detail.SpotWeatherQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spots")
class SpotDetailController {

    private final SpotDetailQueryService spotDetailQueryService;
    private final SpotWeatherQueryService spotWeatherQueryService;

    SpotDetailController(
            SpotDetailQueryService spotDetailQueryService, SpotWeatherQueryService spotWeatherQueryService) {
        this.spotDetailQueryService = spotDetailQueryService;
        this.spotWeatherQueryService = spotWeatherQueryService;
    }

    @Operation(
            summary = "장소 상세",
            description = "장소 하나의 기본 정보와 유형별 상세를 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "박지(BAKJI)이면 서버는 bakji에 설명, 물·화장실 유무, 통신 신호, 다녀온 회원의 확인 수, "
                    + "제보자(memberId, nickname)를 채우고 publicDetail은 null로 준다. "
                    + "야영장(CAMPSITE)과 자연휴양림(FOREST)이면 publicDetail에 원천, 분류, 시설, 연락처, 원천 기준일, "
                    + "운영 상태, 휴장 기간을 채우고 bakji는 null로 준다. 원천에 시설 정보가 없으면 facilities는 null이다. "
                    + "closedNow는 서버가 오늘 한국 날짜로 계산한 휴장 여부다. 원천의 휴장 정보가 정확하지 않아서 "
                    + "서버는 휴장 중인 장소도 숨기지 않고 표시만 한다. "
                    + "parkWarning.warned는 장소가 공원 경계 안일 가능성이 있는지를 뜻한다. 경고가 아니면 warned만 주고, "
                    + "경고이면 공원 이름(areaName), 경계 데이터 출처(source)와 기준일(sourceDate), 참고용 고지(notice), "
                    + "안내(guide)를 함께 준다. 공원 경계 행이 없는 경고에는 areaName, source, sourceDate가 빠진다. "
                    + "날씨는 이 응답에 없고 GET /api/spots/{spotId}/weather로 따로 받는다. 서버는 이 요청에서 외부 API를 부르지 않는다. "
                    + "아직 채우지 않는 항목이 있어서, rating은 average가 null이고 count가 0이며, "
                    + "recentReviews, expectedPeople, recruitingBasecamps는 빈 배열로 준다. "
                    + "장소가 없거나 숨김, 삭제, 검토 대기 상태이면 404 NOT_FOUND로 응답한다. "
                    + "spotId가 숫자가 아니면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/{spotId}")
    SpotDetailResponse findDetail(@PathVariable long spotId) {
        return SpotDetailResponse.from(spotDetailQueryService.findDetail(spotId));
    }

    @Operation(
            summary = "장소 날씨",
            description = "장소 하나의 기상청 단기·중기 예보와 오늘(한국 날짜) 출몰시각(sun)을 weather에 담아 준다. "
                    + "로그인하지 않아도 조회할 수 있다. 화면은 장소 상세와 이 요청을 함께 보낸다. "
                    + "기상청 호출이 실패하거나 늦어도 200으로 응답하고 weather는 null이다. 이때 천문연은 부르지 않는다. "
                    + "기상청은 성공하고 천문연만 실패하면 weather는 채우고 sun만 null로 준다. "
                    + "서버는 같은 격자·같은 발표 시각의 예보와 같은 장소·같은 날짜의 출몰시각을 메모리에 잠시 두고 다시 쓴다. "
                    + "장소가 없거나 숨김, 삭제, 검토 대기 상태이면 외부 API를 부르지 않고 404 NOT_FOUND로 응답한다. "
                    + "spotId가 숫자가 아니면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/{spotId}/weather")
    SpotWeatherResponse findWeather(@PathVariable long spotId) {
        return SpotWeatherResponse.from(
                spotWeatherQueryService.findWeather(spotId).orElse(null));
    }
}
