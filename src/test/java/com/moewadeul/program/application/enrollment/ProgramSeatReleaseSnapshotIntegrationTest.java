package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.LockWaits;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramCancelReason;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest
class ProgramSeatReleaseSnapshotIntegrationTest {

    private static final Instant OVERDUE = MutableClock.DEFAULT_INSTANT.minus(Duration.ofMinutes(1));

    @Autowired
    private ProgramSanctionCleanupService cleanupService;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MySQLContainer mysql;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;
    private long programId;
    private long overdueMemberId;
    private long overdueApplicationId;
    private long seatTakerId;
    private long subscriberId;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
        programId = fixture.saveProgram(2, false);
        fixture.fillSeats(programId, 1);
        overdueMemberId = fixture.saveMember();
        overdueApplicationId = fixture.saveApplication(programId, overdueMemberId, "PENDING_PAYMENT", OVERDUE);
        seatTakerId = fixture.saveMember();
        subscriberId = fixture.saveMember();
        fixture.saveVacancyAlert(programId, subscriberId);
    }

    @Test
    @DisplayName("[F-16][F-20] 제재 정리가 행사 행 잠금을 기다리는 동안 다른 신청이 마지막 자리를 채우면, 기한 지난 신청을 취소해도 빈자리 알림을 내지 않는다")
    void sanctionCleanupSeesSeatTakenWhileWaiting() throws Exception {
        assertNoSeatReleasedWhenSeatTakenDuringWait(
                () -> cleanupService.cleanUp(overdueMemberId, ProgramCancelReason.SANCTIONED));
    }

    @Test
    @DisplayName("[F-19][F-20] 회원 취소가 행사 행 잠금을 기다리는 동안 다른 신청이 마지막 자리를 채우면, 기한 지난 신청을 취소해도 빈자리 알림을 내지 않는다")
    void memberCancelSeesSeatTakenWhileWaiting() throws Exception {
        assertNoSeatReleasedWhenSeatTakenDuringWait(() -> paymentService.cancel(overdueMemberId, overdueApplicationId));
    }

    private void assertNoSeatReleasedWhenSeatTakenDuringWait(Runnable cancel) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection seatTaker = openRootConnection()) {
            seatTaker.setAutoCommit(false);
            execute(seatTaker, "SELECT id FROM program WHERE id = " + programId + " FOR UPDATE");

            Future<?> canceling = executor.submit(cancel);
            LockWaits.await(mysql, 1, "행사 행");
            execute(
                    seatTaker,
                    "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at,"
                            + " updated_at) VALUES (" + programId + ", " + seatTakerId
                            + ", 'CONFIRMED', NOW(6), NOW(6), NOW(6))");
            seatTaker.commit();
            canceling.get(30, TimeUnit.SECONDS);

            assertThat(fixture.applicationStatus(overdueApplicationId)).isEqualTo("CANCELED");
            assertThat(fixture.activeCount(programId)).isEqualTo(2);
            assertThat(fixture.seatReleasedPayloads(programId))
                    .as("정원이 찬 행사에 빈자리 알림이 나갔다")
                    .isEmpty();
            assertThat(fixture.vacancyAlertNotifiedAt(programId, subscriberId)).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private Connection openRootConnection() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
