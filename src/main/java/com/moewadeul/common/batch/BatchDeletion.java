package com.moewadeul.common.batch;

import java.util.function.IntUnaryOperator;

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
}
