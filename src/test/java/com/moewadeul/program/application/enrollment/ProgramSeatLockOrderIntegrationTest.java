package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.LockWaits;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.program.infra.enrollment.SeatClaim;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

// payment 표에 LOCK TABLES를 걸면 FK 때문에 신청 표를 읽는 문장까지 메타데이터 잠금에서 멈추므로 결제는 중복 행으로 멈춘다.
@IntegrationTest
class ProgramSeatLockOrderIntegrationTest {

    private static final String SEAT_COUNT_STATEMENT =
            "com.moewadeul.program.infra.enrollment.ProgramApplicationMapper.insertIfSeatAvailable";
    private static final String STATUS_INDEX = "idx_program_application_program_status";
    private static final String COVERING_INDEX = "idx_program_application_seat_covering";
    private static final int HISTORY_ROWS = 3000;
    private static final Duration PAYMENT_DEADLINE = Duration.ofMinutes(15);
    private static final Instant PAYMENT_DUE_AT = MutableClock.DEFAULT_INSTANT.plus(PAYMENT_DEADLINE);
    private static final Instant AFTER_DUE = PAYMENT_DUE_AT.plusSeconds(1);
    private static final long WAIT_MILLIS = 5_000;

    enum SeatCountPlan {
        OPTIMIZER_CHOICE(null),
        STATUS_INDEX(ProgramSeatLockOrderIntegrationTest.STATUS_INDEX),
        COVERING_INDEX(ProgramSeatLockOrderIntegrationTest.COVERING_INDEX);

        private final String expectedIndex;

        SeatCountPlan(String expectedIndex) {
            this.expectedIndex = expectedIndex;
        }
    }

    @Autowired
    private ProgramApplyService programApplyService;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private ProgramApplicationRepository applicationRepository;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MySQLContainer mysql;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @ParameterizedTest
    @EnumSource(SeatCountPlan.class)
    @DisplayName(
            "[F-18][F-19][NFR-02] 결제가 기한 전 시각을 읽고 반영하기 전에 기한이 지나 들어온 신청은 결제를 기다려 PROGRAM_SOLD_OUT을 받고, 결제는 잠금 실패 없이 확정된다")
    void applyAfterDueWaitsForInFlightPayment(SeatCountPlan plan) throws Exception {
        // given
        long programId = fixture.saveProgram(1, false);
        long payerId = fixture.saveMember();
        long applicantId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, payerId, "PENDING_PAYMENT");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection monitor = openRootConnection();
                AutoCloseable ignored = prepareSeatCountPlan(monitor, plan, programId, applicantId);
                Connection holder = openRootConnection()) {
            holdPaymentRow(holder, applicationId);

            // when
            Future<Object> pay = executor.submit(() -> programPaymentService.pay(payerId, applicationId));
            LockWaits.await(mysql, 1, "결제가 신청 행 잠금을 기다림");
            clock.setInstant(AFTER_DUE);
            Future<Object> apply = executor.submit(() -> programApplyService.apply(applicantId, programId));
            LockWaits.waitUntilBlockedOrDone(mysql, 2, apply);
            holder.rollback();

            // then
            assertThat(outcome(pay)).isInstanceOf(ProgramPayResult.class);
            assertThat(outcome(apply))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_SOLD_OUT));
            assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CONFIRMED");
            assertThat(applicationRepository.countSeatHolders(programId, AFTER_DUE))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @EnumSource(SeatCountPlan.class)
    @DisplayName("[F-18][F-19] 회원 취소가 잠금만 쥐고 반영하기 전에 신청이 들어와도 둘 다 잠금 실패 없이 끝나고, 신청은 자리를 받거나 PROGRAM_SOLD_OUT을 받는다")
    void applyDuringMemberCancelEndsWithoutLockFailure(SeatCountPlan plan) throws Exception {
        // given
        long programId = fixture.saveProgram(1, false);
        long holderId = fixture.saveMember();
        long applicantId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, holderId, "CONFIRMED");
        fixture.savePayment(applicationId, "PAID");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection monitor = openRootConnection();
                AutoCloseable ignored = prepareSeatCountPlan(monitor, plan, programId, applicantId);
                Connection locker = openRootConnection()) {
            execute(locker, "LOCK TABLES outbox_event WRITE");

            // when
            Future<Object> cancel = executor.submit(() -> programPaymentService.cancel(holderId, applicationId));
            awaitWaitingForMetadataLock(monitor, "insert into outbox_event%");
            Future<Object> apply = executor.submit(() -> programApplyService.apply(applicantId, programId));
            LockWaits.waitUntilBlockedOrDone(mysql, 1, apply);
            execute(locker, "UNLOCK TABLES");

            // then
            assertThat(outcome(cancel))
                    .isEqualTo(new ProgramApplicationCancelResult(ProgramApplicationStatus.CANCELED, true));
            Object applied = outcome(apply);
            boolean seated = applied instanceof ProgramApplyResult;
            if (!seated) {
                assertThat(applied)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_SOLD_OUT));
            }
            assertThat(fixture.applicationStatus(applicationId)).isEqualTo("CANCELED");
            assertThat(fixture.paymentStatus(applicationId)).isEqualTo("REFUNDED");
            assertThat(applicationRepository.countSeatHolders(programId, clock.instant()))
                    .isEqualTo(seated ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[F-19] 결제가 반영을 마치기 전에 같은 신청의 회원 취소가 들어와도 결제 확정을 기다린 뒤 환불로 끝난다")
    void cancelRacingInFlightPaymentRefundsIt() throws Exception {
        // given
        long programId = fixture.saveProgram(1, false);
        long memberId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, memberId, "PENDING_PAYMENT");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection holder = openRootConnection()) {
            holdPaymentRow(holder, applicationId);

            // when
            Future<Object> pay = executor.submit(() -> programPaymentService.pay(memberId, applicationId));
            LockWaits.await(mysql, 1, "결제가 신청 행 잠금을 기다림");
            Future<Object> cancel = executor.submit(() -> programPaymentService.cancel(memberId, applicationId));
            LockWaits.waitUntilBlockedOrDone(mysql, 2, cancel);
            holder.rollback();

            // then
            assertThat(outcome(pay)).isInstanceOf(ProgramPayResult.class);
            assertThat(outcome(cancel))
                    .isEqualTo(new ProgramApplicationCancelResult(ProgramApplicationStatus.CANCELED, true));
            assertThat(fixture.paymentStatus(applicationId)).isEqualTo("REFUNDED");
        } finally {
            executor.shutdownNow();
        }
    }

    private AutoCloseable prepareSeatCountPlan(Connection monitor, SeatCountPlan plan, long programId, long applicantId)
            throws Exception {
        if (plan == SeatCountPlan.OPTIMIZER_CHOICE) {
            return () -> {};
        }
        saveHistory(programId);
        AutoCloseable cleanup = () -> {};
        if (plan == SeatCountPlan.COVERING_INDEX) {
            execute(
                    monitor,
                    "CREATE INDEX " + COVERING_INDEX + " ON program_application (program_id, status, payment_due_at)");
            cleanup = () -> execute(monitor, "DROP INDEX " + COVERING_INDEX + " ON program_application");
        }
        try {
            execute(monitor, "ANALYZE TABLE program_application");
            assertThat(seatCountIndex(monitor, programId, applicantId))
                    .as("자리 세기 하위 쿼리가 타는 인덱스")
                    .isEqualTo(plan.expectedIndex);
        } catch (Throwable e) {
            cleanup.close();
            throw e;
        }
        return cleanup;
    }

    private void saveHistory(long programId) {
        long memberId = fixture.saveMember();
        LocalDateTime at = LocalDateTime.ofInstant(MutableClock.DEFAULT_INSTANT, ZoneOffset.UTC);
        jdbc.batchUpdate(
                "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)"
                        + " VALUES (?, ?, 'EXPIRED', ?, ?, ?)",
                IntStream.range(0, HISTORY_ROWS)
                        .mapToObj(i -> new Object[] {programId, memberId, at, at, at})
                        .toList());
    }

    private String seatCountIndex(Connection monitor, long programId, long applicantId) throws SQLException {
        MappedStatement statement = sqlSessionFactory.getConfiguration().getMappedStatement(SEAT_COUNT_STATEMENT);
        SeatClaim claim = new SeatClaim(programId, applicantId, AFTER_DUE.plus(PAYMENT_DEADLINE), AFTER_DUE);
        BoundSql boundSql = statement.getBoundSql(claim);
        try (PreparedStatement explain = monitor.prepareStatement("EXPLAIN " + boundSql.getSql())) {
            new DefaultParameterHandler(statement, claim, boundSql).setParameters(explain);
            try (ResultSet rows = explain.executeQuery()) {
                while (rows.next()) {
                    if ("a".equals(rows.getString("table"))) {
                        return rows.getString("key");
                    }
                }
            }
        }
        throw new IllegalStateException("EXPLAIN 결과에 신청 표의 행이 없습니다.");
    }

    private Connection openRootConnection() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
    }

    // FK 검사를 끄지 않으면 이 INSERT가 신청 행에 공유 잠금을 걸어 결제의 신청 행 잠금과 엉킨다.
    private void holdPaymentRow(Connection holder, long applicationId) throws SQLException {
        holder.setAutoCommit(false);
        execute(holder, "SET foreign_key_checks = 0");
        execute(
                holder,
                "INSERT INTO payment (program_application_id, amount, status, paid_at, created_at, updated_at)"
                        + " VALUES ("
                        + applicationId
                        + ", 30000, 'PAID', NOW(6), NOW(6), NOW(6))");
    }

    private void awaitWaitingForMetadataLock(Connection monitor, String statementPattern)
            throws SQLException, InterruptedException {
        long deadline = deadline();
        while (true) {
            try (PreparedStatement query = monitor.prepareStatement("SELECT COUNT(*) FROM performance_schema.threads"
                    + " WHERE PROCESSLIST_STATE = 'Waiting for table metadata lock' AND PROCESSLIST_INFO LIKE ?")) {
                query.setString(1, statementPattern);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) {
                        return;
                    }
                }
            }
            assertThat(System.nanoTime())
                    .as(statementPattern + " 문장이 메타데이터 잠금에서 기다리기를 기다리다 시간이 지났다")
                    .isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Object outcome(Future<Object> action) throws InterruptedException {
        try {
            return action.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException e) {
            return e;
        }
    }
}
