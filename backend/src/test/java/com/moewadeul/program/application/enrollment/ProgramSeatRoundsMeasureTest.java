package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.infra.enrollment.SeatClaim;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

// 사례 3의 경합 횟수를 세는 측정이다. 회귀 테스트가 아니라서 measure 태그로만 돈다: ./gradlew measureRounds -Drounds=1000
// 두 스레드를 CountDownLatch로 함께 출발시키고 라운드마다 결과를 분류해 build/measure/에 JSON으로 남긴다.
// 기한 경합은 신청 쪽이 0~3ms 늦게 시계를 기한 뒤로 옮긴다(사례 3의 "기한 직후 3ms 안에" 조건).
@IntegrationTest
@Tag("measure")
class ProgramSeatRoundsMeasureTest {

    private static final int ROUNDS = Integer.getInteger("rounds", 1000);
    private static final String SEAT_COUNT_STATEMENT =
            "com.moewadeul.program.infra.enrollment.ProgramApplicationMapper.insertIfSeatAvailable";
    private static final int HISTORY_ROWS = 3000;
    private static final Duration PAYMENT_DEADLINE = Duration.ofMinutes(15);
    private static final Instant PAYMENT_DUE_AT = MutableClock.DEFAULT_INSTANT.plus(PAYMENT_DEADLINE);

    @Autowired
    private ProgramApplyService applyService;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private ProgramVacancyAlertService vacancyAlertService;

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
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void cancelRacesFirstVacancyAlert() throws Exception {
        vacancyRounds("vacancy-first-subscriber", false);
    }

    @Test
    void cancelRacesVacancyAlertWithEarlierSubscriber() throws Exception {
        vacancyRounds("vacancy-with-earlier-subscriber", true);
    }

    @Test
    void payAtDeadlineRacesApplyOnFreshProgram() throws Exception {
        boundaryRounds("boundary-fresh-program", false);
    }

    @Test
    void payAtDeadlineRacesApplyWithHistoryRows() throws Exception {
        boundaryRounds("boundary-history-rows", true);
    }

    @Test
    void payAtDeadlineRacesApplyWithCoveringIndex() throws Exception {
        jdbc.execute(
                "CREATE INDEX idx_program_application_seat_covering ON program_application (program_id, status, payment_due_at)");
        try {
            boundaryRounds("boundary-covering-index", true);
        } finally {
            jdbc.execute("DROP INDEX idx_program_application_seat_covering ON program_application");
        }
    }

    private void vacancyRounds(String name, boolean earlierSubscriber) throws Exception {
        Map<String, Integer> counts = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            long programId = fixture.saveProgram(1, false);
            long holderId = fixture.saveMember();
            long applicationId = fixture.saveApplication(programId, holderId, "PENDING_PAYMENT");
            long subscriberId = fixture.saveMember();
            if (earlierSubscriber) {
                fixture.saveVacancyAlert(programId, fixture.saveMember());
            }

            Object[] outcome = race(
                    () -> paymentService.cancel(holderId, applicationId),
                    () -> vacancyAlertService.subscribe(subscriberId, programId));

            int alerts = fixture.vacancyAlertCount(programId, subscriberId);
            LocalDateTime notifiedAt = alerts == 1 ? fixture.vacancyAlertNotifiedAt(programId, subscriberId) : null;
            boolean stranded = alerts == 1 && notifiedAt == null && fixture.activeCount(programId) < 1;
            count(counts, "cancel=" + label(outcome[0]));
            count(counts, "subscribe=" + label(outcome[1]));
            count(counts, stranded ? "violation=stranded-alert" : (alerts == 1 ? "alert=notified" : "alert=none"));
        }
        write(name, Map.of(), counts);
    }

    private void boundaryRounds(String name, boolean historyRows) throws Exception {
        long programId = fixture.saveProgram(1, false);
        long payerId = fixture.saveMember();
        long applicantId = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, payerId, "PENDING_PAYMENT");
        if (historyRows) {
            saveHistory(programId);
        }
        jdbc.execute("ANALYZE TABLE program_application");
        String seatIndex = seatCountIndex(programId, applicantId);
        Map<String, Integer> counts = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.update("DELETE FROM payment WHERE program_application_id = ?", applicationId);
            jdbc.update("UPDATE program_application SET status = 'PENDING_PAYMENT' WHERE id = ?", applicationId);
            jdbc.update(
                    "DELETE FROM program_application WHERE program_id = ? AND member_id = ?", programId, applicantId);
            clock.setInstant(PAYMENT_DUE_AT.minusMillis(2));

            long jitterNanos = ThreadLocalRandom.current().nextLong(TimeUnit.MILLISECONDS.toNanos(3));
            Object[] outcome = race(() -> paymentService.pay(payerId, applicationId), () -> {
                LockSupport.parkNanos(jitterNanos);
                clock.setInstant(PAYMENT_DUE_AT.plusMillis(1));
                return applyService.apply(applicantId, programId);
            });

            long holders = applicationRepository.countSeatHolders(programId, PAYMENT_DUE_AT.plusMillis(1));
            count(counts, "pay=" + label(outcome[0]));
            count(counts, "apply=" + label(outcome[1]));
            count(counts, holders > 1 ? "violation=over-capacity" : "seats=within-capacity");
        }
        write(name, Map.of("seatCountIndex", String.valueOf(seatIndex)), counts);
    }

    private Object[] race(Callable<Object> first, Callable<Object> second) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        Future<Object> a = executor.submit(() -> {
            start.await();
            return first.call();
        });
        Future<Object> b = executor.submit(() -> {
            start.await();
            return second.call();
        });
        start.countDown();
        return new Object[] {outcome(a), outcome(b)};
    }

    private static Object outcome(Future<Object> action) throws InterruptedException {
        try {
            return action.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            return e;
        }
    }

    private static String label(Object outcome) {
        if (outcome instanceof BusinessException e) {
            return e.getErrorCode().toString();
        }
        if (outcome instanceof Throwable t) {
            return t.getClass().getSimpleName();
        }
        return "ok";
    }

    private static void count(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private void write(String name, Map<String, String> condition, Map<String, Integer> counts) throws IOException {
        StringBuilder json = new StringBuilder("{\"scenario\":\"" + name + "\",\"rounds\":" + ROUNDS);
        condition.forEach(
                (k, v) -> json.append(",\"").append(k).append("\":\"").append(v).append('"'));
        json.append(",\"counts\":{");
        counts.forEach(
                (k, v) -> json.append('"').append(k).append("\":").append(v).append(','));
        json.setLength(json.length() - 1);
        json.append("}}\n");
        Path dir = Path.of("build/measure");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".json"), json);
        System.out.print(json);
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(ROUNDS * 3);
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

    private String seatCountIndex(long programId, long applicantId) throws SQLException {
        Instant afterDue = PAYMENT_DUE_AT.plusMillis(1);
        MappedStatement statement = sqlSessionFactory.getConfiguration().getMappedStatement(SEAT_COUNT_STATEMENT);
        SeatClaim claim = new SeatClaim(programId, applicantId, afterDue.plus(PAYMENT_DEADLINE), afterDue);
        BoundSql boundSql = statement.getBoundSql(claim);
        try (Connection monitor = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                PreparedStatement explain = monitor.prepareStatement("EXPLAIN " + boundSql.getSql())) {
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
}
