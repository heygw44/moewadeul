package com.moewadeul.program.infra.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramPaymentExpiryIndexIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    private static final String SELECT_DUE_SQL = "SELECT id FROM program_application"
            + " WHERE status = 'PENDING_PAYMENT' AND payment_due_at <= ? AND id > 0 ORDER BY id LIMIT 500";

    private static final int CONFIRMED_APPLICATIONS = 300;

    @Autowired
    private ProgramPaymentExpiryMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-19][PG-05] 결제 기한 만료 조회는 (status, payment_due_at) 인덱스로 기한이 지난 결제 대기 신청만 읽는다")
    void dueTargetsAreFoundThroughStatusPaymentDueIndex() {
        // given
        ProgramApplyFixture fixture = new ProgramApplyFixture(jdbc, memberRepository);
        long programId = fixture.saveProgram(CONFIRMED_APPLICATIONS + 1, false);
        saveConfirmedApplications(programId, fixture.saveEmailVerifiedMembers(CONFIRMED_APPLICATIONS));
        long due = fixture.saveApplication(
                programId, fixture.saveMember(), "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        jdbc.execute("ANALYZE TABLE program_application");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + SELECT_DUE_SQL, utc(NOW));
        List<ExpiryTarget> targets = mapper.selectDueTargets(NOW, 0, 500);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_program_application_status_payment_due");
        assertThat(targets).extracting(ExpiryTarget::applicationId).contains(due);
    }

    private void saveConfirmedApplications(long programId, List<Long> memberIds) {
        List<Object[]> rows = new ArrayList<>(memberIds.size());
        for (long memberId : memberIds) {
            rows.add(new Object[] {programId, memberId, utc(NOW.minus(Duration.ofDays(1))), utc(NOW), utc(NOW)});
        }
        jdbc.batchUpdate(
                "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)"
                        + " VALUES (?, ?, 'CONFIRMED', ?, ?, ?)",
                rows);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
