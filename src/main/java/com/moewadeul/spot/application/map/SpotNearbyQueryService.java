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

@Service
@RequiredArgsConstructor
public class SpotNearbyQueryService {

    private static final double FIRST_STEP_RADIUS_KM = 1.0;

    private final SpotNearbyMapper spotNearbyMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SpotNearbyPage findNearby(SpotNearbyQuery query) {
        long offset = (long) query.page() * query.size();
        LocalDate today = KoreaTime.today(clock);
        double radiusKm = searchRadiusKm(query, today, offset + query.size() + 1);
        List<SpotNearbyRow> rows =
                spotNearbyMapper.selectNearby(toCondition(query, radiusKm, today), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<SpotNearbyItem> content =
                rows.stream().limit(query.size()).map(row -> toItem(row, today)).toList();
        return new SpotNearbyPage(content, query.page(), query.size(), hasNext);
    }

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

    private static double toKilometers(double meters) {
        return Math.round(meters / 10.0) / 100.0;
    }
}
