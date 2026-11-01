package com.moewadeul.common.transaction;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ReadCommittedTransaction {

    private ReadCommittedTransaction() {}

    // 바깥 트랜잭션에 합류하면 Spring이 isolation 속성을 조용히 무시하므로, 메서드 안에서 실제 격리 수준을 확인한다.
    public static void require(String reason) {
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (isolation == null || isolation != TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new IllegalStateException("READ COMMITTED 트랜잭션에서 불러야 합니다. " + reason);
        }
    }
}
