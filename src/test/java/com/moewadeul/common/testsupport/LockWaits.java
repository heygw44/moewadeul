package com.moewadeul.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// 동시성 테스트가 "다른 트랜잭션의 행 잠금을 실제로 기다리는 중"임을 sleep 없이 확인하는 도우미다.
// monitor는 테스트 대상 트랜잭션과 다른 연결이어야 하고, performance_schema를 읽을 수 있는 root 계정이어야 한다.
public final class LockWaits {

    private static final long WAIT_MILLIS = 10_000;
    private static final long POLL_MILLIS = 20;

    private LockWaits() {}

    public static void await(Connection monitor, int expected, String description)
            throws SQLException, InterruptedException {
        long deadline = deadline();
        while (count(monitor) < expected) {
            assertThat(System.nanoTime())
                    .as(description + ": 행 잠금 대기 " + expected + "건을 기다리다 시간이 지났다")
                    .isLessThan(deadline);
            Thread.sleep(POLL_MILLIS);
        }
    }

    // 대기가 생기기 전에 action이 끝나는 경로도 호출자가 직접 단언하는 경우를 위해, 시간이 지나도 실패시키지 않고 돌아온다.
    public static void awaitOrDone(Connection monitor, int expected, Future<?> action)
            throws SQLException, InterruptedException {
        long deadline = deadline();
        while (!action.isDone() && count(monitor) < expected && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS);
        }
    }

    private static int count(Connection monitor) throws SQLException {
        try (Statement statement = monitor.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(DISTINCT REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
    }
}
