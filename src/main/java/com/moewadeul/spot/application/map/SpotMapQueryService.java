package com.moewadeul.spot.application.map;

import com.moewadeul.spot.application.SpotClosedNow;
import com.moewadeul.spot.infra.map.SpotAreaCondition;
import com.moewadeul.spot.infra.map.SpotAreaMapper;
import com.moewadeul.spot.infra.map.SpotClusterGrid;
import com.moewadeul.spot.infra.map.SpotClusterRow;
import com.moewadeul.spot.infra.map.SpotGridCells;
import com.moewadeul.spot.infra.map.SpotMarkerRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 화면 영역 안의 ACTIVE 장소를 찾는다.
 *
 * <p>한 응답에는 마커와 묶음을 합쳐 {@value #MAX_ITEMS}개까지만 담는다. 그래서 서버는 영역 안 장소가 {@value #MAX_ITEMS}개 이하이면 모두 마커로
 * 주고, 넘으면 마커를 비우고 영역을 가로세로 {@value #GRID_SIZE}칸씩 나눈 칸별 묶음으로 준다. 칸은 많아야 {@value #GRID_SIZE}×
 * {@value #GRID_SIZE}개라서 묶음도 상한을 넘지 않는다.
 *
 * <p>공공데이터 장소의 휴장 여부는 조회한 날의 한국 날짜로 계산한다. 서버는 기본으로 휴장 중인 장소도 숨기지 않고 표시만
 * 하고, 호출하는 쪽이 휴장 제외를 고를 때만 뺀다.
 */
@Service
@RequiredArgsConstructor
public class SpotMapQueryService {

    static final int MAX_ITEMS = 500;
    static final int GRID_SIZE = 20;

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SpotAreaMapper spotAreaMapper;
    private final Clock clock;

    /**
     * 호출하면 query 조건에 맞는 영역 안 장소를 마커나 칸별 묶음으로 돌려준다. 마커는 장소 ID 순서이고, 묶음은 남쪽 행부터, 같은 행에서는 서쪽 칸부터
     * 나온다.
     *
     * <p>묶음은 장소 테이블이 아니라 미리 세어 둔 격자 집계 표에서 합친다. 넓은 영역은 장소가 수십만 건이라 요청마다 세면 DB CPU가 모자라기 때문이다.
     * 상한을 넘는지는 영역 안 장소 수로 정확히 판단한다. 먼저 영역 안에 통째로 든 격자 칸만 세어 상한을 넘으면 바로 묶음을 주고, 그렇지 않을 때만 마커를
     * 상한보다 한 건 더 읽어 판단한다.
     *
     * <p>입지 필터를 켜면 격자 집계 표에 입지 축이 없어서 먼저 세는 단계를 건너뛰고, 마커를 상한보다 한 건 더 읽어 판단한다. 상한을 넘으면 집계 표
     * 대신 장소를 직접 세어 묶는다. 입지가 있는 장소는 고캠핑 야영장뿐이라 그 수가 적다.
     *
     * <p>모든 조회를 읽기 전용 트랜잭션 하나에서 실행한다. 그래서 MySQL은 모든 조회에 같은 스냅숏을 보여 주고, 그 사이 다른 트랜잭션이 장소를 바꿔도
     * 판단과 묶음 결과가 어긋나지 않는다.
     */
    @Transactional(readOnly = true)
    public SpotAreaResult findInArea(SpotAreaQuery query) {
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        SpotAreaCondition condition = toCondition(query, today);
        SpotClusterGrid grid = toGrid(query);
        SpotGridCells cells = SpotGridCells.covering(
                query.swLat(), query.swLng(), query.neLat(), query.neLng(), Math.min(grid.cellLat(), grid.cellLng()));
        if (!condition.filtersSettings() && countCertainlyInArea(condition, cells) > MAX_ITEMS) {
            return new SpotAreaResult(List.of(), findClusters(condition, grid, cells));
        }
        List<SpotMarkerRow> rows = spotAreaMapper.selectMarkers(condition, MAX_ITEMS + 1);
        if (rows.size() > MAX_ITEMS) {
            return new SpotAreaResult(List.of(), findClusters(condition, grid, cells));
        }
        List<SpotMarker> markers =
                rows.stream().map(row -> toMarker(row, today)).toList();
        return new SpotAreaResult(markers, List.of());
    }

    // 영역 안에 통째로 든 격자 칸의 장소는 모두 영역 안이다. 휴장 제외를 켜면 영역 안의 휴장 장소를 모두 뺀다.
    // 그래서 결과는 실제 영역 안 장소 수보다 크지 않고, 이 값이 상한을 넘으면 실제 수도 상한을 넘는다.
    private long countCertainlyInArea(SpotAreaCondition condition, SpotGridCells cells) {
        long insideCells = spotAreaMapper.countInsideCells(condition, cells);
        if (!condition.hideClosed()) {
            return insideCells;
        }
        return insideCells - spotAreaMapper.countClosedInArea(condition);
    }

    private List<SpotCluster> findClusters(SpotAreaCondition condition, SpotClusterGrid grid, SpotGridCells cells) {
        return selectClusterRows(condition, grid, cells).stream()
                .map(SpotMapQueryService::toCluster)
                .toList();
    }

    // 입지 필터는 격자 집계 표에 없는 조건이라, 켜면 집계 표 대신 장소를 직접 센다.
    private List<SpotClusterRow> selectClusterRows(
            SpotAreaCondition condition, SpotClusterGrid grid, SpotGridCells cells) {
        if (condition.filtersSettings()) {
            return spotAreaMapper.selectSettingClusters(condition, grid);
        }
        return spotAreaMapper.selectClusters(condition, grid, cells);
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
