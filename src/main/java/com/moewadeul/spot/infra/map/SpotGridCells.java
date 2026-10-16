package com.moewadeul.spot.infra.map;

import java.util.List;
import java.util.Optional;
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
     * 호출하면 칸 크기가 maxCellSize(도) 이하인 격자 가운데 가장 큰 단계를 고르고, 영역을 덮는 칸 범위를 돌려준다. 가장 작은 격자도 maxCellSize를
     * 넘으면 단계 0을 고른다. 상한은 호출하는 쪽이 정한다. 격자가 클수록 읽을 행이 줄어 조회가 빨라지지만, 칸 배정이 화면 칸 경계에서 더 어긋난다.
     *
     * <p>범위는 경계에 걸친 칸을 놓치지 않게 양쪽으로 한 칸씩 넓힌다. 어느 칸을 셀지는 매퍼가 칸 중심으로 다시 고른다.
     */
    public static SpotGridCells covering(double swLat, double swLng, double neLat, double neLng, double maxCellSize) {
        int level = 0;
        while (level + 1 < LEVEL_COUNT && cellSize(level + 1) <= maxCellSize) {
            level++;
        }
        double size = cellSize(level);
        List<Integer> rows = IntStream.rangeClosed(cellIndex(swLat, size) - 1, cellIndex(neLat, size) + 1)
                .boxed()
                .toList();
        return new SpotGridCells(level, rows, cellIndex(swLng, size) - 1, cellIndex(neLng, size) + 1);
    }

    /**
     * 호출하면 중심이 영역(경계선 포함) 안에 드는 격자 칸이 maxCells개 이하인 단계 가운데 가장 작은 단계를 골라, 그 칸을 정확히 덮는 범위를 돌려준다.
     * 가장 큰 단계로도 maxCells개를 넘으면 빈 값을 돌려준다. rows와 열 범위는 {@link #covering}과 달리 여유 칸 없이 중심이 영역 안인 칸만 가리킨다.
     * 영역 안에 중심이 든 칸이 없으면 rows가 비어 있다.
     *
     * <p>중심 판정은 매퍼의 SQL과 같은 식, {@code (번호 + 0.5) × 칸 크기}다. 실수 오차로 Java와 SQL의 판정이 갈리지 않게, 나눗셈으로 구한 번호를 같은 식으로 다시
     * 확인해서 맞춘다.
     */
    public static Optional<SpotGridCells> fixedClusterCells(
            double swLat, double swLng, double neLat, double neLng, int maxCells) {
        for (int level = 0; level < LEVEL_COUNT; level++) {
            double size = cellSize(level);
            int firstRow = firstCenterInside(swLat, size);
            int lastRow = lastCenterInside(neLat, size);
            int firstColumn = firstCenterInside(swLng, size);
            int lastColumn = lastCenterInside(neLng, size);
            long rowCount = Math.max(0, (long) lastRow - firstRow + 1);
            long columnCount = Math.max(0, (long) lastColumn - firstColumn + 1);
            if (rowCount * columnCount <= maxCells) {
                List<Integer> rows =
                        IntStream.rangeClosed(firstRow, lastRow).boxed().toList();
                return Optional.of(new SpotGridCells(level, rows, firstColumn, lastColumn));
            }
        }
        return Optional.empty();
    }

    /** 호출하면 영역 안에 읽을 칸이 하나도 없는지 돌려준다. 이때 매퍼에 넘기면 안 되고, 묶음은 빈 목록이다. */
    public boolean isEmpty() {
        return rows.isEmpty() || fromColumn > toColumn;
    }

    // 중심이 low 이상인 가장 작은 칸 번호
    private static int firstCenterInside(double low, double size) {
        int index = (int) Math.ceil(low / size - 0.5);
        while ((index - 1 + 0.5) * size >= low) {
            index--;
        }
        while ((index + 0.5) * size < low) {
            index++;
        }
        return index;
    }

    // 중심이 high 이하인 가장 큰 칸 번호
    private static int lastCenterInside(double high, double size) {
        int index = (int) Math.floor(high / size - 0.5);
        while ((index + 1 + 0.5) * size <= high) {
            index++;
        }
        while ((index + 0.5) * size > high) {
            index--;
        }
        return index;
    }

    static double cellSize(int level) {
        return BASE_CELL_SIZE * Math.pow(2, level);
    }

    private static int cellIndex(double degree, double size) {
        return (int) Math.floor(degree / size);
    }
}
