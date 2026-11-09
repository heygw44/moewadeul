package com.moewadeul.spot.api.map;

import com.moewadeul.common.web.PageResponse;
import com.moewadeul.spot.application.map.SpotMapQueryService;
import com.moewadeul.spot.application.map.SpotNearbyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spots")
class SpotMapController {

    private final SpotMapQueryService spotMapQueryService;
    private final SpotNearbyQueryService spotNearbyQueryService;

    SpotMapController(SpotMapQueryService spotMapQueryService, SpotNearbyQueryService spotNearbyQueryService) {
        this.spotMapQueryService = spotMapQueryService;
        this.spotNearbyQueryService = spotNearbyQueryService;
    }

    @Operation(
            summary = "지도 영역 조회",
            description = "남서쪽 좌표(swLat, swLng)와 북동쪽 좌표(neLat, neLng)로 정한 화면 영역 안의 장소를 돌려준다. "
                    + "경계선 위의 장소도 포함하고, 로그인하지 않아도 조회할 수 있다. "
                    + "위도는 -90~90, 경도는 -180~180이고 남서쪽 값이 북동쪽 값보다 작아야 한다. "
                    + "zoom은 카카오맵 레벨 1~14이며, 서버는 범위만 검증하고 계산에는 쓰지 않는다. "
                    + "types에 CAMPSITE, FOREST, BAKJI를 쉼표로 구분해 주면 그 유형만 돌려주고, 주지 않으면 모든 유형을 돌려준다. "
                    + "hasWater나 hasToilet이 true이면 서버는 해당 시설이 있는 박지만 남기고, 야영장과 자연휴양림은 이 값과 상관없이 포함한다. "
                    + "excludeWarning이 true이면 공원 경계 경고가 붙은 장소를 뺀다. "
                    + "settings에 입지(MOUNTAIN, FOREST, VALLEY, BEACH, ISLAND, RIVER, LAKE, CITY)를 쉼표로 구분해 주면 "
                    + "그중 하나라도 해당하는 장소만 남긴다. "
                    + "조건에 맞는 장소가 500개 이하이면 모두 markers로 주고 clusters는 빈 배열로 준다. "
                    + "500개를 넘으면 markers를 비우고, 지도에 고정된 격자 중 영역 안 칸이 400개 이하인 가장 작은 격자로 묶어 장소가 있는 칸마다 "
                    + "장소 수(count)와 칸 안 장소들의 평균 좌표(lat, lng)를 clusters로 준다. "
                    + "가장 큰 격자로도 칸이 400개를 넘는 넓은 영역은 화면을 가로세로 20칸으로 나눠 묶는다. "
                    + "closedNow는 서버가 오늘 한국 날짜로 계산한 휴장 여부다. 원천의 휴장 정보가 정확하지 않아서 "
                    + "서버는 기본으로 휴장 중인 장소도 숨기지 않고 표시만 한다. hideClosed가 true이면 휴장 중인 장소를 뺀다. "
                    + "박지의 closedNow는 항상 false다. "
                    + "파라미터가 빠졌거나 범위를 벗어났거나, 남서쪽 값이 북동쪽 값보다 작지 않거나, "
                    + "긴 변이 짧은 변의 10배를 넘거나, 모르는 유형이나 입지이면 "
                    + "400 INVALID_INPUT으로 응답한다.")
    @GetMapping
    SpotAreaResponse findInArea(@Valid @ParameterObject @ModelAttribute SpotAreaRequest request) {
        return SpotAreaResponse.from(spotMapQueryService.findInArea(request.toQuery()));
    }

    @Operation(
            summary = "반경 검색",
            description = "중심 좌표(lat, lng)에서 반경 radiusKm 안에 있는 장소를 가까운 순서로 돌려준다. "
                    + "로그인하지 않아도 조회할 수 있다. "
                    + "distanceKm는 중심에서 장소까지의 거리(km)이고, 서버가 소수 둘째 자리까지 반올림해서 준다. "
                    + "위도는 -90~90, 경도는 -180~180이고, 반경은 0보다 크고 50 이하여야 한다. "
                    + "반경이 50을 넘으면 400 SPOT_RADIUS_TOO_LARGE로 응답한다. "
                    + "types, settings, hasWater, hasToilet, excludeWarning, hideClosed는 지도 영역 조회와 같다. 예를 들어 hasWater가 true이면 "
                    + "서버는 물이 있는 박지만 남기고, 야영장과 자연휴양림은 이 값과 상관없이 포함한다. "
                    + "closedNow는 서버가 오늘 한국 날짜로 계산한 휴장 여부다. 원천의 휴장 정보가 정확하지 않아서 "
                    + "서버는 기본으로 휴장 중인 장소도 숨기지 않고 표시만 한다. hideClosed가 true이면 휴장 중인 장소를 뺀다. "
                    + "박지의 closedNow는 항상 false다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고, 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "그 밖에 파라미터가 빠졌거나 범위를 벗어났거나 모르는 유형이나 입지이면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/nearby")
    PageResponse<SpotNearbyItemResponse> findNearby(@Valid @ParameterObject @ModelAttribute SpotNearbyRequest request) {
        return PageResponse.from(spotNearbyQueryService.findNearby(request.toQuery()), SpotNearbyItemResponse::from);
    }
}
