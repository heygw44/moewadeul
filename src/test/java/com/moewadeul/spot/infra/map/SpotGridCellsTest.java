package com.moewadeul.spot.infra.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotGridCellsTest {

    @Test
    @DisplayName("[F-03] 칸 크기 상한 이하인 격자 가운데 가장 큰 단계를 고르고, 상한과 같은 크기도 고른다")
    void choosesLargestGridLevelNotOverMaxCellSize() {
        // given: 상한 0.05도에서 0.04도(단계 4)는 넘지 않고, 0.08도(단계 5)는 넘는다.
        // 상한이 0.02도이면 같은 크기인 단계 3을 고른다.
        // when
        SpotGridCells belowNextLevel = SpotGridCells.covering(36.0, 127.0, 37.0, 128.0, 0.05);
        SpotGridCells equalToLevel = SpotGridCells.covering(36.0, 127.0, 37.0, 128.0, 0.02);

        // then
        assertThat(belowNextLevel.level()).isEqualTo(4);
        assertThat(SpotGridCells.cellSize(belowNextLevel.level())).isEqualTo(0.04);
        assertThat(equalToLevel.level()).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-03] 칸 크기 상한이 아주 작으면 가장 작은 격자를, 아주 크면 가장 큰 격자를 고른다")
    void clampsGridLevelToSmallestAndLargest() {
        // when
        SpotGridCells tiny = SpotGridCells.covering(37.5, 127.0, 37.501, 127.001, 0.00005);
        SpotGridCells huge = SpotGridCells.covering(-90.0, -180.0, 90.0, 180.0, 18.0);

        // then
        assertThat(tiny.level()).isZero();
        assertThat(huge.level()).isEqualTo(SpotGridCells.LEVEL_COUNT - 1);
    }

    @Test
    @DisplayName("[F-03] 읽을 칸 범위는 영역에 걸친 격자 칸보다 양쪽으로 한 칸씩 넓다")
    void coversAreaWithOneCellMarginOnEachSide() {
        // given: 0.02도 격자에서 위도 36.01은 1800번 행, 36.05는 1802번 행이다.
        // when
        SpotGridCells cells = SpotGridCells.covering(36.01, 127.01, 36.05, 127.05, 0.02);

        // then
        assertThat(cells.rows()).containsExactly(1799, 1800, 1801, 1802, 1803);
        assertThat(cells.fromColumn()).isEqualTo(6349);
        assertThat(cells.toColumn()).isEqualTo(6353);
    }

    @Test
    @DisplayName("[F-03] 고정 격자는 중심이 영역 안인 칸이 400개 이하인 가장 작은 단계를 고른다")
    void fixedClusterCellsChoosesSmallestLevelWithinMaxCells() {
        // given: 위도·경도 1도 영역에서 0.04도 격자는 25×25칸(625개), 0.08도 격자는 13×13칸(169개 이하)이다.
        // when
        SpotGridCells cells =
                SpotGridCells.fixedClusterCells(36.0, 127.0, 37.0, 128.0, 400).orElseThrow();

        // then
        assertThat(cells.level()).isEqualTo(5);
        assertThat(cells.rows().size() * (cells.toColumn() - cells.fromColumn() + 1))
                .isLessThanOrEqualTo(400);
    }

    @Test
    @DisplayName("[F-03] 고정 격자는 칸이 정확히 400개인 단계도 고른다")
    void fixedClusterCellsAllowsExactlyMaxCells() {
        // given: 0.0025도 격자에서 위도·경도 0.05도 폭이면 중심이 영역 안인 칸이 20×20=400개다.
        // 중심은 (번호 + 0.5) × 0.0025이므로, 영역의 남서 꼭짓점을 0번 칸 중심으로, 북동 꼭짓점을 19번 칸 중심으로 잡는다. 경계선 위의 중심도 영역 안이다.
        double south = 0.5 * 0.0025;
        double north = 19.5 * 0.0025;

        // when
        SpotGridCells exact =
                SpotGridCells.fixedClusterCells(south, south, north, north, 400).orElseThrow();
        SpotGridCells overByOne = SpotGridCells.fixedClusterCells(south, south, 20.5 * 0.0025, north, 400)
                .orElseThrow();

        // then
        assertThat(exact.level()).isZero();
        assertThat(exact.rows()).hasSize(20);
        assertThat(exact.toColumn() - exact.fromColumn() + 1).isEqualTo(20);
        assertThat(overByOne.level()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-03] 가장 큰 단계로도 칸이 400개를 넘으면 고정 격자를 고르지 않는다")
    void fixedClusterCellsIsEmptyWhenEvenLargestLevelExceedsMaxCells() {
        // given: 0.32도 격자에서 10도 × 10도는 31×31칸(961개)이다. 5도 × 5도는 0.16도 격자로 31×31칸(961개)이라 넘지만 0.32도 격자로는 15×15칸 안팎이다.
        // when / then
        assertThat(SpotGridCells.fixedClusterCells(30.0, 120.0, 40.0, 130.0, 400))
                .isEmpty();
        assertThat(SpotGridCells.fixedClusterCells(30.0, 120.0, 35.0, 125.0, 400))
                .isPresent()
                .get()
                .extracting(SpotGridCells::level)
                .isEqualTo(7);
    }

    @Test
    @DisplayName("[F-03] 고정 격자의 행과 열은 중심이 영역 안인 칸만 정확히 가리킨다")
    void fixedClusterCellsCoverExactlyCellsWithCenterInside() {
        // given: 0.08도 격자(단계 5)에서 위도 36.10~37.07은 중심이 36.12(451번 행)부터 37.0(462번 행)까지다.
        // 450번 행(중심 36.04)은 남쪽 밖이고, 463번 행(중심 37.08)은 북쪽 밖이다.
        // when
        SpotGridCells cells = SpotGridCells.fixedClusterCells(36.10, 127.02, 37.07, 128.02, 400)
                .orElseThrow();

        // then
        assertThat(cells.level()).isEqualTo(5);
        assertThat(cells.rows()).first().isEqualTo(451);
        assertThat(cells.rows()).last().isEqualTo(462);
        assertThat(cells.rows()).hasSize(12);
        assertThat(cells.fromColumn()).isEqualTo(1588);
        assertThat(cells.toColumn()).isEqualTo(1599);
        assertThat(cells.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("[F-03] 영역 안에 칸 중심이 하나도 없으면 빈 범위를 돌려준다")
    void fixedClusterCellsIsEmptyRangeWhenNoCenterInside() {
        // given: 0.0025도 격자의 중심은 0.00125, 0.00375...이다. 0.0001도 폭의 영역은 중심을 품지 못한다.
        // when
        SpotGridCells cells = SpotGridCells.fixedClusterCells(36.0001, 127.0001, 36.0002, 127.0002, 400)
                .orElseThrow();

        // then
        assertThat(cells.level()).isZero();
        assertThat(cells.isEmpty()).isTrue();
    }
}
