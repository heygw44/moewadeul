package com.moewadeul.common.transaction;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 신고는 대상 행을 FOR UPDATE로 읽는다. 그 행을 다른 트랜잭션이 쥐고 있으면 같은 대상의 신고가 커넥션을 하나씩 든 채 잠금 대기 한도까지 기다리고,
 * 신고가 풀 크기만큼 몰리면 지도 같은 읽기 요청이 커넥션을 얻지 못해 함께 멈춘다. 그래서 이 쓰기들은 커넥션을 잡기 전에 허가를 받게 해 풀의 일부만
 * 쓰게 한다. 행사 신청처럼 자체 잠금 설계가 있는 경로는 여기를 거치지 않는다.
 */
@Component
public class RowLockWritePermits {

    static final int MAX_CONCURRENT_WRITES = 6;
    private static final long PERMIT_WAIT_SECONDS = 5;

    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_WRITES, true);

    public void run(Runnable write) {
        call(() -> {
            write.run();
            return null;
        });
    }

    public <T> T call(Supplier<T> write) {
        OutsideTransaction.require("허가를 기다리는 동안 커넥션을 쥐면 풀을 아끼려는 목적이 사라진다.");
        acquire();
        try {
            return write.get();
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        try {
            if (permits.tryAcquire(PERMIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new BusinessException(CommonErrorCode.RESOURCE_BUSY);
    }
}
