package com.moewadeul.spot.application.map;

import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.application.SpotClosedNow;
import com.moewadeul.spot.infra.map.SpotAreaCondition;
import com.moewadeul.spot.infra.map.SpotAreaMapper;
import com.moewadeul.spot.infra.map.SpotClusterGrid;
import com.moewadeul.spot.infra.map.SpotClusterRow;
import com.moewadeul.spot.infra.map.SpotGridCells;
import com.moewadeul.spot.infra.map.SpotMarkerRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotMapQueryService {

    static final int MAX_ITEMS = 500;
    static final int GRID_SIZE = 20;

    static final int MAX_CLUSTER_CELLS = 400;

    static final double CLUSTER_CELL_RATIO = 1.0;

    static final double COUNT_CELL_RATIO = 2.0;

    private final SpotAreaMapper spotAreaMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SpotAreaResult findInArea(SpotAreaQuery query) {
        LocalDate today = LocalDate.now(clock.withZone(KoreaTime.ZONE));
        SpotAreaCondition condition = toCondition(query, today);
        SpotClusterGrid grid = toGrid(query);
        double viewCellSize = Math.min(grid.cellLat(), grid.cellLng());
        SpotGridCells countCells = coveringArea(query, viewCellSize * COUNT_CELL_RATIO);
        if (!condition.filtersSettings() && countCertainlyInArea(condition, countCells) > MAX_ITEMS) {
            return new SpotAreaResult(List.of(), findClusters(condition, query, grid, viewCellSize));
        }
        List<SpotMarkerRow> rows = spotAreaMapper.selectMarkers(condition, MAX_ITEMS + 1);
        if (rows.size() > MAX_ITEMS) {
            return new SpotAreaResult(List.of(), findClusters(condition, query, grid, viewCellSize));
        }
        List<SpotMarker> markers =
                rows.stream().map(row -> toMarker(row, today)).toList();
        return new SpotAreaResult(markers, List.of());
    }

    private long countCertainlyInArea(SpotAreaCondition condition, SpotGridCells cells) {
        long insideCells = spotAreaMapper.countInsideCells(condition, cells);
        if (!condition.hideClosed()) {
            return insideCells;
        }
        return insideCells - spotAreaMapper.countClosedInArea(condition);
    }

    private List<SpotCluster> findClusters(
            SpotAreaCondition condition, SpotAreaQuery query, SpotClusterGrid grid, double viewCellSize) {
        return selectClusterRows(condition, query, grid, viewCellSize).stream()
                .map(SpotMapQueryService::toCluster)
                .toList();
    }

    private List<SpotClusterRow> selectClusterRows(
            SpotAreaCondition condition, SpotAreaQuery query, SpotClusterGrid grid, double viewCellSize) {
        if (condition.filtersSettings()) {
            return spotAreaMapper.selectSettingClusters(condition, grid);
        }
        Optional<SpotGridCells> fixedCells = SpotGridCells.fixedClusterCells(
                query.swLat(), query.swLng(), query.neLat(), query.neLng(), MAX_CLUSTER_CELLS);
        if (fixedCells.isPresent()) {
            return fixedCells.get().isEmpty()
                    ? List.of()
                    : spotAreaMapper.selectGridClusters(condition, fixedCells.get());
        }
        return spotAreaMapper.selectClusters(condition, grid, coveringArea(query, viewCellSize * CLUSTER_CELL_RATIO));
    }

    private static SpotGridCells coveringArea(SpotAreaQuery query, double maxCellSize) {
        return SpotGridCells.covering(query.swLat(), query.swLng(), query.neLat(), query.neLng(), maxCellSize);
    }

    private static SpotClusterGrid toGrid(SpotAreaQuery query) {
        return new SpotClusterGrid(
                (query.neLat() - query.swLat()) / GRID_SIZE,
                (query.neLng() - query.swLng()) / GRID_SIZE,
                GRID_SIZE - 1);
    }

    private static SpotAreaCondition toCondition(SpotAreaQuery query, LocalDate today) {
        return new SpotAreaCondition(
                query.swLat(),
                query.swLng(),
                query.neLat(),
                query.neLng(),
                query.types(),
                query.settings(),
                query.hasWater(),
                query.hasToilet(),
                query.excludeWarning(),
                query.hideClosed(),
                today);
    }

    private static SpotMarker toMarker(SpotMarkerRow row, LocalDate today) {
        return new SpotMarker(
                row.spotId(), row.type(), row.name(), row.lat(), row.lng(), row.parkWarning(), isClosedOn(row, today));
    }

    private static boolean isClosedOn(SpotMarkerRow row, LocalDate today) {
        return SpotClosedNow.isClosedOn(row.type(), row.operatingStatus(), row.closedFrom(), row.closedUntil(), today);
    }

    private static SpotCluster toCluster(SpotClusterRow row) {
        return new SpotCluster(row.lat(), row.lng(), row.spotCount());
    }
}
