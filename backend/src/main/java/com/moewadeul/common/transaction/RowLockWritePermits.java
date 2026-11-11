package com.moewadeul.common.transaction;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 신고는 대상 행을 FOR UPDATE로 읽는다. 그 행을 다른 트랜잭션이 쥐고 있으면 같은 대상의 신고가 커넥션을 하나씩 든 채 잠금 대기 한도까지 기다리고,
 * 신고가 풀 크기만큼 몰리면 지도 같은 읽기 요청이 커넥션을 얻지 못해 함께 멈춘다. 그래서 이 쓰기들은 커넥션을 잡기 전에 허가를 받게 해 풀의 일부만
 * 쓰게 한다. 행사 신청처럼 자체 잠금 설계가 있는 경로는 여기를 거치지 않는다.
 */
@Component
public class RowLockWritePermits {

    static final int MAX_CONCURRENT_WRITES = 6;
    private static final Duration PERMIT_WAIT = Duration.ofSeconds(5);
    private static final String IN_USE_GAUGE_NAME = "moewadeul.row.lock.writes.in.use";
    private static final String REJECTED_COUNTER_NAME = "moewadeul.row.lock.writes.rejected";
    private static final String PERMIT_WAIT_TIMER_NAME = "moewadeul.row.lock.writes.permit.wait";

    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_WRITES, true);
    private final Duration permitWait;
    private final Counter rejected;
    private final Timer acquiredWait;
    private final Timer rejectedWait;

    @Autowired
    public RowLockWritePermits(MeterRegistry meterRegistry) {
        this(meterRegistry, PERMIT_WAIT);
    }

    RowLockWritePermits(MeterRegistry meterRegistry, Duration permitWait) {
        this.permitWait = permitWait;
        Gauge.builder(IN_USE_GAUGE_NAME, permits, p -> MAX_CONCURRENT_WRITES - p.availablePermits())
                .description("행 잠금 쓰기 허가 중 쓰이고 있는 수")
                .register(meterRegistry);
        this.rejected = Counter.builder(REJECTED_COUNTER_NAME)
                .description("허가를 기다리다 시간이 다하거나 끊겨 503으로 거절한 수")
                .register(meterRegistry);
        this.acquiredWait = waitTimer(meterRegistry, "acquired");
        this.rejectedWait = waitTimer(meterRegistry, "rejected");
    }

    private static Timer waitTimer(MeterRegistry meterRegistry, String outcome) {
        return Timer.builder(PERMIT_WAIT_TIMER_NAME)
                .description("행 잠금 쓰기 허가를 기다린 시간")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

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
        long start = System.nanoTime();
        try {
            if (permits.tryAcquire(permitWait.toNanos(), TimeUnit.NANOSECONDS)) {
                acquiredWait.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        rejectedWait.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        rejected.increment();
        throw new BusinessException(CommonErrorCode.RESOURCE_BUSY);
    }
}
