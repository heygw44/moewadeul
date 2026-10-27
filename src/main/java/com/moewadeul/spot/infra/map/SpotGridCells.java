package com.moewadeul.spot.infra.map;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * rows는 읽을 격자 행 번호 목록이다. 매퍼는 행 번호를 범위 대신 목록으로 넘긴다. 그래야 MySQL이 (단계, 행, 열) 기본 키에서 행마다 열 범위만 읽고,
 * 영역 밖 열까지 훑지 않는다.
 */
public record SpotGridCells(int level, List<Integer> rows, int fromColumn, int toColumn) {

    static final double BASE_CELL_SIZE = 0.0025;
    static final int LEVEL_COUNT = 8;

    public SpotGridCells {
        rows = List.copyOf(rows);
    }

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

    public double cellSize() {
        return cellSize(level);
    }

    public boolean isEmpty() {
        return rows.isEmpty() || fromColumn > toColumn;
    }

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

    static int cellIndex(double degree, double size) {
        return (int) Math.floor(degree / size);
    }
}
