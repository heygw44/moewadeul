package com.moewadeul.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testcontainers.mysql.MySQLContainer;

public final class LockWaits {

    private static final long WAIT_MILLIS = 10_000;
    private static final long POLL_MILLIS = 20;

    private LockWaits() {}

    public static void await(MySQLContainer mysql, int expected, String description)
            throws SQLException, InterruptedException {
        long deadline = deadline();
        try (Connection monitor = openRootConnection(mysql)) {
            while (count(monitor) < expected) {
                assertThat(System.nanoTime())
                        .as(description + ": 행 잠금 대기 " + expected + "건을 기다리다 시간이 지났다")
                        .isLessThan(deadline);
                Thread.sleep(POLL_MILLIS);
            }
        }
    }

    public static void waitUntilBlockedOrDone(MySQLContainer mysql, int expected, Future<?> action)
            throws SQLException, InterruptedException {
        long deadline = deadline();
        try (Connection monitor = openRootConnection(mysql)) {
            while (!action.isDone() && count(monitor) < expected && System.nanoTime() < deadline) {
                Thread.sleep(POLL_MILLIS);
            }
        }
    }

    private static Connection openRootConnection(MySQLContainer mysql) throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
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
