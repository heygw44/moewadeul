package com.moewadeul.common.transaction;

import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class InsideTransaction {

    private InsideTransaction() {}

    public static void require(String reason) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("트랜잭션 안에서 불러야 합니다. " + reason);
        }
    }
}
