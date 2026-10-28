package com.moewadeul.spot.infra.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.SpotClosedNow;
import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotClosureSqlParityIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final double LAT = 37.5665;
    private static final double LNG = 126.978;

    @Autowired
    private SpotAreaMapper spotAreaMapper;

    @Autowired
    private SpotNearbyMapper spotNearbyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-03] 휴장 제외 조회는 SpotClosedNow가 오늘 휴장이라고 보는 장소만 정확히 뺀다")
    void hideClosedQueriesExcludeExactlySpotsClosedToday() {
        // given
        Map<Long, ClosureCase> cases = insertEveryCase();
        Set<Long> closedIds = cases.entrySet().stream()
                .filter(entry -> entry.getValue().closedOn(TODAY))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<Long> openIds =
                cases.keySet().stream().filter(id -> !closedIds.contains(id)).collect(Collectors.toSet());

        // when
        Set<Long> markerIds = spotAreaMapper.selectMarkers(area(true), cases.size() + 1).stream()
                .map(SpotMarkerRow::spotId)
                .collect(Collectors.toSet());
        Set<Long> allMarkerIds = spotAreaMapper.selectMarkers(area(false), cases.size() + 1).stream()
                .map(SpotMarkerRow::spotId)
                .collect(Collectors.toSet());
        long closedInArea = spotAreaMapper.countClosedInArea(area(true));
        SpotGridCells cells =
                SpotGridCells.fixedClusterCells(37.0, 126.5, 38.0, 127.5, 400).orElseThrow();
        long clusteredCount = spotAreaMapper.selectGridClusters(area(true), cells).stream()
                .mapToLong(SpotClusterRow::spotCount)
                .sum();
        Set<Long> nearbyIds = spotNearbyMapper.selectNearby(nearby(), 0, cases.size() + 1).stream()
                .map(SpotNearbyRow::spotId)
                .collect(Collectors.toSet());
        long nearbyCount = spotNearbyMapper.countNearbyUpTo(nearby(), cases.size() + 1);

        // then
        assertThat(closedIds).isNotEmpty();
        assertThat(allMarkerIds).isEqualTo(cases.keySet());
        assertThat(markerIds).isEqualTo(openIds);
        assertThat(closedInArea).isEqualTo(closedIds.size());
        assertThat(clusteredCount).isEqualTo(openIds.size());
        assertThat(nearbyIds).isEqualTo(openIds);
        assertThat(nearbyCount).isEqualTo(openIds.size());
    }

    @Test
    @DisplayName("[F-03] 어떤 날이든 휴장일 수 있는 공공 장소는 closure_candidate가 TRUE다")
    void everySpotClosedOnSomeDateIsClosureCandidate() {
        // given
        Map<Long, ClosureCase> cases = insertEveryCase();
        List<LocalDate> dates = Stream.iterate(TODAY.minusDays(2), date -> date.plusDays(1))
                .limit(5)
                .toList();

        // when
        Map<Long, Boolean> candidates = new HashMap<>();
        jdbcTemplate.query("SELECT spot_id, closure_candidate FROM public_spot_detail", row -> {
            candidates.put(row.getLong("spot_id"), row.getBoolean("closure_candidate"));
        });

        // then
        List<Long> closedSomeDayIds = cases.entrySet().stream()
                .filter(entry -> dates.stream().anyMatch(entry.getValue()::closedOn))
                .map(Map.Entry::getKey)
                .toList();
        assertThat(closedSomeDayIds).isNotEmpty();
        assertThat(closedSomeDayIds)
                .allSatisfy(id ->
                        assertThat(candidates.get(id)).as("%s", cases.get(id)).isTrue());
    }

    private Map<Long, ClosureCase> insertEveryCase() {
        List<String> statuses = new ArrayList<>();
        statuses.add(null);
        Arrays.stream(PublicSpotOperatingStatus.values()).map(Enum::name).forEach(statuses::add);
        List<LocalDate> dates = Arrays.asList(null, TODAY.minusDays(1), TODAY, TODAY.plusDays(1));
        Map<Long, ClosureCase> cases = new HashMap<>();
        for (SpotType type : SpotType.values()) {
            ClosureCase withoutDetail = new ClosureCase(type, false, null, null, null);
            cases.put(insertSpot(withoutDetail), withoutDetail);
            for (String status : statuses) {
                for (LocalDate from : dates) {
                    for (LocalDate until : dates) {
                        ClosureCase closureCase = new ClosureCase(type, true, status, from, until);
                        cases.put(insertSpot(closureCase), closureCase);
                    }
                }
            }
        }
        return cases;
    }

    private long insertSpot(ClosureCase closureCase) {
        String name = TestSequence.unique("휴장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                closureCase.type().name(),
                name,
                "POINT(" + LAT + " " + LNG + ")");
        long spotId = jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        if (closureCase.hasDetail()) {
            jdbcTemplate.update(
                    "INSERT INTO public_spot_detail"
                            + " (spot_id, source, external_id, operating_status, closed_from, closed_until,"
                            + " synced_at, created_at, updated_at)"
                            + " VALUES (?, 'GOCAMPING', ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))",
                    spotId,
                    String.valueOf(spotId),
                    closureCase.operatingStatus(),
                    closureCase.closedFrom(),
                    closureCase.closedUntil());
        }
        return spotId;
    }

    private static SpotAreaCondition area(boolean hideClosed) {
        return new SpotAreaCondition(
                37.0, 126.5, 38.0, 127.5, Set.of(), Set.of(), false, false, false, hideClosed, TODAY);
    }

    private static SpotNearbyCondition nearby() {
        return new SpotNearbyCondition(
                LAT, LNG, 1000, 37.0, 126.5, 38.0, 127.5, Set.of(), Set.of(), false, false, false, true, TODAY);
    }

    private record ClosureCase(
            SpotType type, boolean hasDetail, String operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {

        boolean closedOn(LocalDate date) {
            return SpotClosedNow.isClosedOn(type, operatingStatus, closedFrom, closedUntil, date);
        }
    }
}
