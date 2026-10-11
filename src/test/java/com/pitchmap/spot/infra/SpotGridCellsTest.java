package com.pitchmap.spot.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotGridCellsTest {

    @Test
    @DisplayName("[F-03] 화면 칸 크기의 절반 이하인 격자 가운데 가장 큰 단계를 고른다")
    void choosesLargestGridLevelNotOverHalfOfViewCell() {
        // given: 화면 칸 0.05도의 절반은 0.025도다. 0.02도(단계 3)는 넘지 않고, 0.04도(단계 4)는 넘는다.
        double viewCell = 0.05;

        // when
        SpotGridCells cells = SpotGridCells.covering(36.0, 127.0, 37.0, 128.0, viewCell);

        // then
        assertThat(cells.level()).isEqualTo(3);
        assertThat(SpotGridCells.cellSize(cells.level())).isEqualTo(0.02);
    }

    @Test
    @DisplayName("[F-03] 화면 칸이 아주 작으면 가장 작은 격자를, 아주 크면 가장 큰 격자를 고른다")
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
        SpotGridCells cells = SpotGridCells.covering(36.01, 127.01, 36.05, 127.05, 0.05);

        // then
        assertThat(cells.rows()).containsExactly(1799, 1800, 1801, 1802, 1803);
        assertThat(cells.fromColumn()).isEqualTo(6349);
        assertThat(cells.toColumn()).isEqualTo(6353);
    }
}
