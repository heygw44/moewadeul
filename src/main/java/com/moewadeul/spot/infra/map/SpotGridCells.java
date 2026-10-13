package com.moewadeul.spot.infra.map;

import java.util.List;
import java.util.stream.IntStream;

/**
 * {@link SpotAreaMapper}가 묶음 집계 표에서 읽을 격자 단계와 칸 범위. 격자 칸 크기는 {@value #BASE_CELL_SIZE}도 × 2^단계이고, 단계는 0부터
 * {@value #LEVEL_COUNT}개다. 이 값은 집계 표를 채우는 마이그레이션의 프로시저와 같아야 한다.
 *
 * <p>rows는 읽을 격자 행 번호 목록이다. 매퍼는 행 번호를 범위 대신 목록으로 넘긴다. 그래야 MySQL이 (단계, 행, 열) 기본 키에서 행마다 열 범위만 읽고,
 * 영역 밖 열까지 훑지 않는다.
 */
public record SpotGridCells(int level, List<Integer> rows, int fromColumn, int toColumn) {

    static final double BASE_CELL_SIZE = 0.0025;
    static final int LEVEL_COUNT = 8;

    public SpotGridCells {
        rows = List.copyOf(rows);
    }

    /**
     * 호출하면 화면 칸 크기(viewCellSize, 도)의 절반 이하인 격자 가운데 가장 큰 단계를 고르고, 영역을 덮는 칸 범위를 돌려준다. 가장 작은 격자도 절반을
     * 넘으면 단계 0을 고른다. 격자 칸이 화면 칸보다 충분히 작아야 칸 배정이 화면 칸 경계에서 덜 어긋난다.
     *
     * <p>범위는 경계에 걸친 칸을 놓치지 않게 양쪽으로 한 칸씩 넓힌다. 어느 칸을 셀지는 매퍼가 칸 중심으로 다시 고른다.
     */
    public static SpotGridCells covering(double swLat, double swLng, double neLat, double neLng, double viewCellSize) {
        int level = 0;
        while (level + 1 < LEVEL_COUNT && cellSize(level + 1) <= viewCellSize / 2) {
            level++;
        }
        double size = cellSize(level);
        List<Integer> rows = IntStream.rangeClosed(cellIndex(swLat, size) - 1, cellIndex(neLat, size) + 1)
                .boxed()
                .toList();
        return new SpotGridCells(level, rows, cellIndex(swLng, size) - 1, cellIndex(neLng, size) + 1);
    }

    static double cellSize(int level) {
        return BASE_CELL_SIZE * Math.pow(2, level);
    }

    private static int cellIndex(double degree, double size) {
        return (int) Math.floor(degree / size);
    }
}
