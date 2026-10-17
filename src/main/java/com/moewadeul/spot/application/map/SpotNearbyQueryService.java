package com.moewadeul.spot.application.map;

import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.application.SpotClosedNow;
import com.moewadeul.spot.application.SpotSearchBox;
import com.moewadeul.spot.infra.map.SpotNearbyCondition;
import com.moewadeul.spot.infra.map.SpotNearbyMapper;
import com.moewadeul.spot.infra.map.SpotNearbyRow;
import java.time.Clock;
import java.time.LocalDate;
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

    // 단계 확장의 첫 반경(km). 장소가 몰린 곳은 이 안에 한 페이지가 다 들어와서 첫 단계에서 끝난다.
    private static final double FIRST_STEP_RADIUS_KM = 1.0;

    private final SpotNearbyMapper spotNearbyMapper;
    private final Clock clock;

    /**
     * 호출하면 query 조건에 맞는 반경 안 장소를 가까운 순서로 한 페이지 돌려준다. 거리가 같은 장소는 장소 ID 순서로 나온다.
     *
     * <p>서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     *
     * <p>반경 안 장소를 모두 정렬하면 비용이 반경 안 장소 수에 비례한다. 장소가 몰린 곳에서는 반경 50km 안에 수십만 곳이 들어간다. 그래서 서비스는
     * 작은 반경에서 시작해 두 배씩 넓히면서, 이 페이지까지 필요한 개수(건너뛸 행 + 페이지 크기 + 1)가 모이는지 센다. 모이면 그 반경으로만 정렬해서 읽는다.
     * 결과는 처음 요청한 반경으로 읽은 것과 같다. 작은 반경 안에 필요한 개수가 있으면, 그 밖의 장소는 모두 그 반경보다 멀어서 앞자리에 끼지 못하기 때문이다.
     */
    @Transactional(readOnly = true)
    public SpotNearbyPage findNearby(SpotNearbyQuery query) {
        long offset = (long) query.page() * query.size();
        LocalDate today = LocalDate.now(clock.withZone(KoreaTime.ZONE));
        double radiusKm = searchRadiusKm(query, today, offset + query.size() + 1);
        List<SpotNearbyRow> rows =
                spotNearbyMapper.selectNearby(toCondition(query, radiusKm, today), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<SpotNearbyItem> content =
                rows.stream().limit(query.size()).map(row -> toItem(row, today)).toList();
        return new SpotNearbyPage(content, query.page(), query.size(), hasNext);
    }

    // 반경을 두 배씩 넓히다가 needed개 이상 모이는 첫 반경을 돌려준다. 요청한 반경까지 넓혀도 모자라면 요청한 반경을 돌려준다.
    private double searchRadiusKm(SpotNearbyQuery query, LocalDate today, long needed) {
        double radiusKm = Math.min(FIRST_STEP_RADIUS_KM, query.radiusKm());
        while (radiusKm < query.radiusKm()
                && spotNearbyMapper.countNearbyUpTo(toCondition(query, radiusKm, today), needed) < needed) {
            radiusKm = Math.min(radiusKm * 2, query.radiusKm());
        }
        return radiusKm;
    }

    private static SpotNearbyCondition toCondition(SpotNearbyQuery query, double radiusKm, LocalDate today) {
        SpotSearchBox box = SpotSearchBox.around(query.lat(), query.lng(), radiusKm);
        return new SpotNearbyCondition(
                query.lat(),
                query.lng(),
                radiusKm * 1000,
                box.swLat(),
                box.swLng(),
                box.neLat(),
                box.neLng(),
                query.types(),
                query.settings(),
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
