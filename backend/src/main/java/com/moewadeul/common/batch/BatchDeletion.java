package com.moewadeul.common.batch;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

public final class BatchDeletion {

    private BatchDeletion() {}

    public static int untilDrained(int batchSize, IntUnaryOperator deleteBatch) {
        int total = 0;
        int deleted;
        do {
            deleted = deleteBatch.applyAsInt(batchSize);
            total += deleted;
        } while (deleted >= batchSize);
        return total;
    }

    /**
     * 고른 id만 갱신해 갱신문이 읽는 행 전부에 잠금을 걸지 않게 한다.
     * 고른 뒤 조건에서 벗어난 행은 갱신문이 건너뛰므로, 한 건도 못 바꾼 배치에서는 멈춘다.
     */
    public static int untilDrainedByIds(
            int batchSize, IntFunction<List<Long>> selectIds, ToIntFunction<List<Long>> updateByIds) {
        int total = 0;
        List<Long> ids;
        int updated;
        do {
            ids = selectIds.apply(batchSize);
            updated = ids.isEmpty() ? 0 : updateByIds.applyAsInt(ids);
            total += updated;
        } while (ids.size() >= batchSize && updated > 0);
        return total;
    }
}
