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
}
