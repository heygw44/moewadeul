package com.moewadeul.trust.application.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.ErrorCode;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.LockWaits;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.trust.domain.TrustErrorCode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest
class IdentityVerificationConcurrencyIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private IdentityVerificationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MySQLContainer mysql;

    @RepeatedTest(5)
    @DisplayName("[ID-01] 같은 회원이 동시에 본인확인을 10번 요청하면 1건만 저장되고 나머지는 IDENTITY_ALREADY_VERIFIED다")
    void sameMemberIsVerifiedOnce() throws Exception {
        // given
        long memberId = memberFixture.save().getId();

        // when
        List<Throwable> failures = runConcurrently(
                THREADS,
                index -> service.verify(memberId, new IdentityVerifyCommand(1995, Gender.FEMALE, "demo-" + index)));

        // then
        assertFailuresAre(failures, THREADS - 1, TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        assertThat(rowCount()).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[ID-04] 서로 다른 회원 10명이 같은 시연용 식별 문자열로 동시에 본인확인하면 1건만 저장되고 나머지는 IDENTITY_CI_DUPLICATED다")
    void sameCiIsLinkedToOneMember() throws Exception {
        // given
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(memberFixture.save().getId());
        }

        // when
        List<Throwable> failures = runConcurrently(
                THREADS,
                index -> service.verify(
                        memberIds.get(index), new IdentityVerifyCommand(1995, Gender.MALE, "same-person")));

        // then
        assertFailuresAre(failures, THREADS - 1, TrustErrorCode.IDENTITY_CI_DUPLICATED);
        assertThat(rowCount()).isEqualTo(1);
    }

    @RepeatedTest(10)
    @DisplayName(
            "[ID-04] 같은 CI의 본인확인 행을 지운 직후 서로 다른 회원 10명이 그 CI로 동시에 본인확인해도, DB 교착 상태로 실패하는 요청 없이 1건만 저장되고 나머지는 IDENTITY_CI_DUPLICATED다")
    void sameCiAfterDeletedRowIsLinkedToOneMember() throws Exception {
        // given
        // 지운 행은 InnoDB가 정리하기 전까지 같은 키의 삭제 표시 레코드로 인덱스에 남는다.
        service.verify(memberFixture.save().getId(), new IdentityVerifyCommand(1995, Gender.MALE, "reused-person"));
        jdbc.update("DELETE FROM identity_verification");
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(memberFixture.save().getId());
        }

        // when
        List<Throwable> failures = runConcurrently(
                THREADS,
                index -> service.verify(
                        memberIds.get(index), new IdentityVerifyCommand(1995, Gender.MALE, "reused-person")));

        // then
        assertFailuresAre(failures, THREADS - 1, TrustErrorCode.IDENTITY_CI_DUPLICATED);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[ID-04] 지운 행의 삭제 표시 레코드에 모든 요청의 중복 검사가 겹쳐도 교착 상태 없이 1건만 저장되고 나머지는 IDENTITY_CI_DUPLICATED다")
    void sameCiOverlappingOnDeletedRowDoesNotDeadlock() throws Exception {
        // given
        // 교착 상태는 두 요청의 중복 검사가 삽입 직전 틈에서 겹쳐야만 난다. 틈을 벌려 두려고 삭제 표시 레코드를 정리되지 않게 하고 공유 잠금을 쥔 채 요청을 출발시킨다.
        try (Connection monitor = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement statement = monitor.createStatement()) {
            statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
            service.verify(memberFixture.save().getId(), new IdentityVerifyCommand(1995, Gender.MALE, "held-person"));
            String ciHash = jdbc.queryForObject("SELECT ci_hash FROM identity_verification", String.class);
            jdbc.update("DELETE FROM identity_verification");
            statement.execute("SELECT id FROM identity_verification WHERE ci_hash = '" + ciHash + "' FOR SHARE");
            List<Long> memberIds = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                memberIds.add(memberFixture.save().getId());
            }
            long deadlocksBefore = deadlockCount(statement);

            // when
            ExecutorService releaser = Executors.newSingleThreadExecutor();
            try {
                Future<List<Throwable>> verifying = releaser.submit(() -> runConcurrently(
                        THREADS,
                        index -> service.verify(
                                memberIds.get(index), new IdentityVerifyCommand(1995, Gender.MALE, "held-person"))));
                LockWaits.waitUntilBlockedOrDone(mysql, THREADS, verifying);
                statement.execute("COMMIT");

                // then
                assertFailuresAre(
                        verifying.get(60, TimeUnit.SECONDS), THREADS - 1, TrustErrorCode.IDENTITY_CI_DUPLICATED);
            } finally {
                releaser.shutdown();
            }
            assertThat(rowCount()).isEqualTo(1);
            assertThat(deadlockCount(statement)).isEqualTo(deadlocksBefore);
        }
    }

    @Test
    @DisplayName("[ID-04] 저장하다 실제 DB 교착 상태의 희생자가 되면 다시 실행해서 본인확인을 끝낸다")
    void retriesWhenChosenAsDeadlockVictim() throws Exception {
        // given
        // 잠금 연결이 지운 CI를 쥐면 요청은 회원 키를 넣은 채 CI에서 멈춘다. 그 회원 키를 잠금 연결이 넣으려 하면 고리가 생기고, 먼저 넣은 행들이 있는 잠금 연결은 무거워서 요청이 희생된다.
        try (Connection snapshot = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement snapshotStatement = snapshot.createStatement();
                Connection locker = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement lockerStatement = locker.createStatement()) {
            snapshotStatement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
            service.verify(memberFixture.save().getId(), new IdentityVerifyCommand(1995, Gender.MALE, "victim-person"));
            String ciHash = jdbc.queryForObject("SELECT ci_hash FROM identity_verification", String.class);
            jdbc.update("DELETE FROM identity_verification");
            long memberId = memberFixture.save().getId();
            lockerStatement.execute("START TRANSACTION");
            lockerStatement.execute("SELECT id FROM identity_verification WHERE ci_hash = '" + ciHash + "' FOR UPDATE");
            for (char suffix : "abcdef".toCharArray()) {
                lockerStatement.executeUpdate(insertIdentity(
                        memberFixture.save().getId(), String.valueOf(suffix).repeat(64)));
            }
            long deadlocksBefore = deadlockCount(snapshotStatement);

            // when
            ExecutorService requester = Executors.newSingleThreadExecutor();
            try {
                Future<IdentityVerificationResult> verifying = requester.submit(
                        () -> service.verify(memberId, new IdentityVerifyCommand(1995, Gender.MALE, "victim-person")));
                LockWaits.await(mysql, 1, "요청이 지운 CI에서 멈춘다");
                lockerStatement.executeUpdate(insertIdentity(memberId, "9".repeat(64)));
                lockerStatement.execute("ROLLBACK");

                // then
                assertThat(verifying.get(30, TimeUnit.SECONDS).identityVerified())
                        .isTrue();
            } finally {
                requester.shutdown();
            }
            assertThat(deadlockCount(snapshotStatement)).isEqualTo(deadlocksBefore + 1);
            assertThat(jdbc.queryForObject("SELECT member_id FROM identity_verification", Long.class))
                    .isEqualTo(memberId);
        }
    }

    @Test
    @DisplayName(
            "[ID-04] 한 회원의 요청 1건과 다른 회원의 요청 3건이 같은 CI로 섞여도 30라운드 동안 교착 상태 없이 라운드마다 1건만 저장되고, 진 요청은 이긴 쪽에 맞는 거절을 받는다")
    void mixedMembersOnSameCiDoNotDeadlock() throws Exception {
        try (Connection monitor = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement statement = monitor.createStatement()) {
            long deadlocksBefore = deadlockCount(statement);

            for (int round = 0; round < 30; round++) {
                // given
                long single = memberFixture.save().getId();
                long repeated = memberFixture.save().getId();
                String person = "mixed-" + round;

                // when
                List<Throwable> failures = runConcurrently(
                        4,
                        index -> service.verify(
                                index == 0 ? single : repeated, new IdentityVerifyCommand(1995, Gender.MALE, person)));

                // then
                assertThat(errorCodes(failures))
                        .as("한 회원이 이기면 다른 회원의 세 요청이 모두 CI 중복이고, 다른 회원이 이기면 그 회원의 나머지 두 요청은 이미 인증, 한 회원은 CI 중복이다")
                        .isIn(
                                List.of(
                                        TrustErrorCode.IDENTITY_CI_DUPLICATED,
                                        TrustErrorCode.IDENTITY_CI_DUPLICATED,
                                        TrustErrorCode.IDENTITY_CI_DUPLICATED),
                                List.of(
                                        TrustErrorCode.IDENTITY_ALREADY_VERIFIED,
                                        TrustErrorCode.IDENTITY_ALREADY_VERIFIED,
                                        TrustErrorCode.IDENTITY_CI_DUPLICATED));
                assertThat(rowCount()).isEqualTo(round + 1);
            }
            assertThat(deadlockCount(statement)).isEqualTo(deadlocksBefore);
        }
    }

    @Test
    @DisplayName("[ID-04] 이웃한 CI 10개의 행을 모두 지운 직후 다른 회원들이 각자 그 CI로 동시에 본인확인해도 150라운드 동안 교착 상태가 없다")
    void neighbouringDeletedCisDoNotDeadlock() throws Exception {
        try (Connection monitor = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement statement = monitor.createStatement()) {
            long deadlocksBefore = deadlockCount(statement);

            for (int round = 0; round < 150; round++) {
                // given
                // 스냅숏을 쥐어 삭제 표시 레코드가 정리되지 않고 이웃 키 사이에 남게 한다.
                statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
                String prefix = "neighbour-" + round + "-";
                for (int i = 0; i < THREADS; i++) {
                    service.verify(
                            memberFixture.save().getId(), new IdentityVerifyCommand(1995, Gender.MALE, prefix + i));
                }
                jdbc.update("DELETE FROM identity_verification");
                List<Long> memberIds = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    memberIds.add(memberFixture.save().getId());
                }

                // when
                List<Throwable> failures = runConcurrently(
                        THREADS,
                        index -> service.verify(
                                memberIds.get(index), new IdentityVerifyCommand(1995, Gender.MALE, prefix + index)));
                statement.execute("COMMIT");

                // then
                assertThat(failures).isEmpty();
                assertThat(rowCount()).isEqualTo(THREADS);
            }
            assertThat(deadlockCount(statement)).isEqualTo(deadlocksBefore);
        }
    }

    private static String insertIdentity(long memberId, String ciHash) {
        return "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at, created_at, updated_at)"
                + " VALUES (" + memberId + ", 1995, 'MALE', '" + ciHash + "', 'FAKE', NOW(6), NOW(6), NOW(6))";
    }

    private static long deadlockCount(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT `COUNT` FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void assertFailuresAre(List<Throwable> failures, int expectedCount, TrustErrorCode expectedCode) {
        assertThat(failures)
                .hasSize(expectedCount)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(expectedCode)));
    }

    private static List<ErrorCode> errorCodes(List<Throwable> failures) {
        assertThat(failures).allSatisfy(failure -> assertThat(failure).isInstanceOf(BusinessException.class));
        return failures.stream()
                .map(failure -> ((BusinessException) failure).getErrorCode())
                .sorted(Comparator.comparing(ErrorCode::toString))
                .toList();
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM identity_verification", Integer.class);
    }

    private List<Throwable> runConcurrently(int threads, IndexedAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(index);
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<Object>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }

    @FunctionalInterface
    private interface IndexedAction {
        Object run(int index) throws Exception;
    }
}
