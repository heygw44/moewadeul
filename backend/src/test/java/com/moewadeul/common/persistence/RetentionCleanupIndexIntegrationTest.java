package com.moewadeul.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import com.moewadeul.member.infra.verification.EmailVerificationCleanupMapper;
import com.moewadeul.outbox.infra.OutboxEventCleanupMapper;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.trust.infra.identity.CiRetentionCleanupMapper;
import com.moewadeul.trust.infra.sanction.SanctionExpiryMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class RetentionCleanupIndexIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant PAST = NOW.minus(Duration.ofDays(30));
    private static final Instant FUTURE = NOW.plus(Duration.ofDays(30));
    private static final int KEPT_ROWS = 300;
    private static final int LIMIT = 500;

    @Autowired
    private EmailVerificationCleanupMapper emailVerificationMapper;

    @Autowired
    private PasswordResetTokenMapper passwordResetTokenMapper;

    @Autowired
    private CiRetentionCleanupMapper ciRetentionMapper;

    @Autowired
    private OutboxEventCleanupMapper outboxMapper;

    @Autowired
    private SanctionExpiryMapper sanctionMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Long> memberIds;
    private long dueMemberId;

    @BeforeEach
    void saveMembers() {
        ProgramApplyFixture fixture = new ProgramApplyFixture(jdbc, memberRepository);
        memberIds = fixture.saveEmailVerifiedMembers(KEPT_ROWS);
        dueMemberId = fixture.saveMember();
    }

    @Test
    @DisplayName("[F-01] 인증 코드 정리는 created_at 인덱스로 기한이 지난 행만 읽고 지운다")
    void emailVerificationCleanupUsesCreatedAtIndex() {
        // given
        List<Object[]> rows = new ArrayList<>();
        for (long memberId : memberIds) {
            rows.add(new Object[] {memberId, "a".repeat(64), utc(FUTURE), utc(NOW), utc(NOW)});
        }
        jdbc.batchUpdate(
                "INSERT INTO email_verification (member_id, code_hash, request_ip, attempt_count, expires_at,"
                        + " created_at, updated_at) VALUES (?, ?, '127.0.0.1', 0, ?, ?, ?)",
                rows);
        jdbc.update(
                "INSERT INTO email_verification (member_id, code_hash, request_ip, attempt_count, expires_at,"
                        + " created_at, updated_at) VALUES (?, ?, '127.0.0.1', 0, ?, ?, ?)",
                dueMemberId,
                "b".repeat(64),
                utc(PAST),
                utc(PAST),
                utc(PAST));
        jdbc.execute("ANALYZE TABLE email_verification");

        // when
        Map<String, Object> plan = jdbc.queryForMap(
                "EXPLAIN DELETE FROM email_verification WHERE created_at < ? LIMIT " + LIMIT, utc(NOW.minusSeconds(1)));
        int deleted = emailVerificationMapper.deleteOlderThan(NOW.minusSeconds(1), LIMIT);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_email_verification_created_at");
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01] 비밀번호 재설정 토큰 정리는 expires_at 인덱스로 만료된 행만 읽고 지운다")
    void passwordResetTokenCleanupUsesExpiresAtIndex() {
        // given
        List<Object[]> rows = new ArrayList<>();
        int sequence = 0;
        for (long memberId : memberIds) {
            rows.add(new Object[] {memberId, "%064d".formatted(sequence++), utc(FUTURE), utc(NOW), utc(NOW)});
        }
        jdbc.batchUpdate(
                "INSERT INTO password_reset_token (member_id, token_hash, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                rows);
        jdbc.update(
                "INSERT INTO password_reset_token (member_id, token_hash, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                dueMemberId,
                "%064d".formatted(sequence),
                utc(PAST),
                utc(PAST),
                utc(PAST));
        jdbc.execute("ANALYZE TABLE password_reset_token");

        // when
        Map<String, Object> plan = jdbc.queryForMap(
                "EXPLAIN DELETE FROM password_reset_token WHERE expires_at <= ? LIMIT " + LIMIT, utc(NOW));
        int deleted = passwordResetTokenMapper.deleteExpiredBefore(NOW, LIMIT);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_password_reset_token_expires_at");
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01] CI 보관 만료 정리는 ci_retained_until 인덱스로 기한이 지난 행만 읽고 지운다")
    void ciRetentionCleanupUsesRetainedUntilIndex() {
        // given
        List<Object[]> rows = new ArrayList<>();
        for (long memberId : memberIds) {
            rows.add(new Object[] {memberId, "%064d".formatted(memberId), utc(NOW), utc(NOW), utc(NOW)});
        }
        jdbc.batchUpdate(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                rows);
        jdbc.update(
                "INSERT INTO identity_verification (member_id, ci_hash, provider, verified_at, ci_retained_until,"
                        + " created_at, updated_at) VALUES (?, ?, 'FAKE', ?, ?, ?, ?)",
                dueMemberId,
                "%064d".formatted(dueMemberId),
                utc(PAST),
                utc(PAST),
                utc(PAST),
                utc(PAST));
        jdbc.execute("ANALYZE TABLE identity_verification");

        // when
        Map<String, Object> plan = jdbc.queryForMap(
                "EXPLAIN DELETE FROM identity_verification"
                        + " WHERE ci_retained_until IS NOT NULL AND ci_retained_until <= ? LIMIT " + LIMIT,
                utc(NOW));
        int deleted = ciRetentionMapper.deleteRetentionExpired(NOW, LIMIT);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_identity_verification_ci_retained_until");
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01] 발행 완료 이벤트 정리는 (status, published_at) 인덱스로 기한이 지난 행만 읽고 지운다")
    void outboxCleanupUsesStatusPublishedAtIndex() {
        // given
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < KEPT_ROWS; i++) {
            rows.add(new Object[] {utc(NOW), utc(NOW), utc(NOW)});
        }
        jdbc.batchUpdate(
                "INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, payload, status, published_at,"
                        + " created_at, updated_at) VALUES ('TEST_EVENT', 'TEST', 1, '{}', 'PUBLISHED', ?, ?, ?)",
                rows);
        jdbc.update(
                "INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, payload, status, published_at,"
                        + " created_at, updated_at) VALUES ('TEST_EVENT', 'TEST', 1, '{}', 'PUBLISHED', ?, ?, ?)",
                utc(PAST),
                utc(PAST),
                utc(PAST));
        jdbc.execute("ANALYZE TABLE outbox_event");

        // when
        Map<String, Object> plan = jdbc.queryForMap(
                "EXPLAIN DELETE FROM outbox_event WHERE status = 'PUBLISHED' AND published_at < ? LIMIT " + LIMIT,
                utc(NOW.minusSeconds(1)));
        int deleted = outboxMapper.deletePublishedBefore(NOW.minusSeconds(1), LIMIT);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_outbox_event_status_published_at");
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01] 제재 만료 처리는 (status, ends_at) 인덱스로 기한이 지난 활성 제재만 읽고 만료시킨다")
    void sanctionExpiryUsesStatusEndsAtIndex() {
        // given
        List<Object[]> rows = new ArrayList<>();
        for (long memberId : memberIds) {
            rows.add(new Object[] {memberId, utc(PAST), utc(FUTURE)});
        }
        jdbc.batchUpdate(
                "INSERT INTO sanction (member_id, type, level, reason, starts_at, ends_at, status, created_at,"
                        + " updated_at) VALUES (?, 'SUSPEND_7D', 1, '테스트', ?, ?, 'ACTIVE', NOW(6), NOW(6))",
                rows);
        jdbc.update(
                "INSERT INTO sanction (member_id, type, level, reason, starts_at, ends_at, status, created_at,"
                        + " updated_at) VALUES (?, 'SUSPEND_7D', 1, '테스트', ?, ?, 'ACTIVE', NOW(6), NOW(6))",
                dueMemberId,
                utc(PAST),
                utc(PAST));
        jdbc.execute("ANALYZE TABLE sanction");

        // when
        Map<String, Object> plan = jdbc.queryForMap(
                "EXPLAIN UPDATE sanction SET status = 'EXPIRED', updated_at = ?"
                        + " WHERE status = 'ACTIVE' AND ends_at IS NOT NULL AND ends_at <= ?",
                utc(NOW),
                utc(NOW));
        int expired = sanctionMapper.expireEnded(NOW);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_sanction_status_ends_at");
        assertThat(expired).isEqualTo(1);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
