package com.pitchmap.spot.application;

import com.pitchmap.spot.infra.SpotNearbyCondition;
import com.pitchmap.spot.infra.SpotNearbyMapper;
import com.pitchmap.spot.infra.SpotNearbyRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중심 좌표에서 반경 안에 있는 ACTIVE 장소를 가까운 순서로 찾는다.
 *
 * <p>공공데이터 장소의 휴장 여부는 조회한 날의 한국 날짜로 계산한다. 서버는 기본으로 휴장 중인 장소도 빼지 않고 표시만 하고,
 * 호출하는 쪽이 휴장 제외를 고를 때만 뺀다.
 */
@Service
@RequiredArgsConstructor
public class SpotNearbyQueryService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SpotNearbyMapper spotNearbyMapper;
    private final Clock clock;

    /**
     * 호출하면 query 조건에 맞는 반경 안 장소를 가까운 순서로 한 페이지 돌려준다. 거리가 같은 장소는 장소 ID 순서로 나온다.
     *
     * <p>서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public SpotNearbyPage findNearby(SpotNearbyQuery query) {
        long offset = (long) query.page() * query.size();
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        List<SpotNearbyRow> rows = spotNearbyMapper.selectNearby(toCondition(query, today), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<SpotNearbyItem> content =
                rows.stream().limit(query.size()).map(row -> toItem(row, today)).toList();
        return new SpotNearbyPage(content, query.page(), query.size(), hasNext);
    }

    private static SpotNearbyCondition toCondition(SpotNearbyQuery query, LocalDate today) {
        SpotSearchBox box = SpotSearchBox.around(query.lat(), query.lng(), query.radiusKm());
        return new SpotNearbyCondition(
                query.lat(),
                query.lng(),
                query.radiusKm() * 1000,
                box.swLat(),
                box.swLng(),
                box.neLat(),
                box.neLng(),
                query.types(),
                query.hasWater(),
                query.hasToilet(),
                query.excludeWarning(),
                query.hideClosed(),
                today);
    }

    private static SpotNearbyItem toItem(SpotNearbyRow row, LocalDate today) {
        boolean closedNow =
                SpotClosedNow.isClosedOn(row.type(), row.operatingStatus(), row.closedFrom(), row.closedUntil(), today);
        return new SpotNearbyItem(
                row.spotId(),
                row.type(),
                row.name(),
                row.lat(),
                row.lng(),
                row.parkWarning(),
                closedNow,
                toKilometers(row.distanceMeters()));
    }

    // 미터를 km로 바꾸면서 소수 둘째 자리에서 반올림한다. 예를 들어 12,345m는 12.35km가 된다.
    private static double toKilometers(double meters) {
        return Math.round(meters / 10.0) / 100.0;
    }
}
