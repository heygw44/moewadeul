package com.moewadeul.program.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;

import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ProgramApplyFixture {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    private final JdbcTemplate jdbc;
    private final MemberJpaRepository memberRepository;

    public ProgramApplyFixture(JdbcTemplate jdbc, MemberJpaRepository memberRepository) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
    }

    public long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    public long saveVerifiedMember() {
        long memberId = saveMember();
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return memberId;
    }

    public long saveProgram(int capacity, boolean overnight) {
        return saveProgram(
                capacity, overnight, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(5)), "SCHEDULED");
    }

    public long saveProgram(int capacity, boolean overnight, Instant applyOpenAt, Instant applyCloseAt, String status) {
        long adminId = saveMember();
        Instant startAt = NOW.plus(Duration.ofDays(7));
        jdbc.update(
                "INSERT INTO program (title, description, location_text, start_at, end_at, capacity, fee,"
                        + " apply_open_at, apply_close_at, payment_deadline_minutes, overnight, status, created_by,"
                        + " created_at, updated_at) VALUES ('가을 백패킹', '설명', '설악산 입구', ?, ?, ?, 30000, ?, ?, 15, ?, ?, ?,"
                        + " ?, ?)",
                utc(startAt),
                utc(startAt.plus(Duration.ofDays(1))),
                capacity,
                utc(applyOpenAt),
                utc(applyCloseAt),
                overnight,
                status,
                adminId,
                utc(NOW),
                utc(NOW));
        return jdbc.queryForObject("SELECT MAX(id) FROM program", Long.class);
    }

    public long saveApplication(long programId, long memberId, String status) {
        jdbc.update(
                "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                programId,
                memberId,
                status,
                utc(NOW.plus(Duration.ofMinutes(15))),
                utc(NOW),
                utc(NOW));
        return jdbc.queryForObject("SELECT MAX(id) FROM program_application", Long.class);
    }

    public long saveApplication(long programId, long memberId, String status, Instant paymentDueAt) {
        jdbc.update(
                "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                programId,
                memberId,
                status,
                utc(paymentDueAt),
                utc(NOW),
                utc(NOW));
        return jdbc.queryForObject("SELECT MAX(id) FROM program_application", Long.class);
    }

    public void savePayment(long applicationId, String status) {
        jdbc.update(
                "INSERT INTO payment (program_application_id, amount, status, paid_at, created_at, updated_at)"
                        + " VALUES (?, 30000, ?, ?, ?, ?)",
                applicationId,
                status,
                utc(NOW),
                utc(NOW),
                utc(NOW));
    }

    public String applicationStatus(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM program_application WHERE id = ?", String.class, applicationId);
    }

    public int paymentCount(long applicationId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE program_application_id = ?", Integer.class, applicationId);
    }

    public String paymentStatus(long applicationId) {
        return jdbc.queryForObject(
                "SELECT status FROM payment WHERE program_application_id = ?", String.class, applicationId);
    }

    public int outboxCount(String eventType, long aggregateId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                Integer.class,
                eventType,
                aggregateId);
    }

    public int activeCount(long programId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM program_application WHERE program_id = ?"
                        + " AND status IN ('PENDING_PAYMENT', 'CONFIRMED')",
                Integer.class,
                programId);
    }

    public int applicationCount(long programId, long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM program_application WHERE program_id = ? AND member_id = ?",
                Integer.class,
                programId,
                memberId);
    }

    public void saveVacancyAlert(long programId, long memberId) {
        jdbc.update(
                "INSERT INTO program_vacancy_alert (program_id, member_id, created_at) VALUES (?, ?, ?)",
                programId,
                memberId,
                utc(NOW));
    }

    public List<Long> saveEmailVerifiedMembers(int count) {
        long firstSeq = TestSequence.next();
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String suffix = firstSeq + "-" + i;
            rows.add(new Object[] {"bulk-" + suffix + "@example.com", "b-" + suffix, utc(NOW), utc(NOW), utc(NOW)});
        }
        jdbc.batchUpdate(
                "INSERT INTO member (email, nickname, status, role, email_verified_at, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', ?, ?, ?)",
                rows);
        return jdbc.queryForList(
                "SELECT id FROM member WHERE email LIKE ? ORDER BY id", Long.class, "bulk-" + firstSeq + "-%");
    }

    public void saveVacancyAlerts(long programId, List<Long> memberIds) {
        jdbc.batchUpdate(
                "INSERT INTO program_vacancy_alert (program_id, member_id, created_at) VALUES (?, ?, ?)",
                memberIds.stream()
                        .map(memberId -> new Object[] {programId, memberId, utc(NOW)})
                        .toList());
    }

    public int vacancyAlertCount(long programId, long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM program_vacancy_alert WHERE program_id = ? AND member_id = ?",
                Integer.class,
                programId,
                memberId);
    }

    public LocalDateTime vacancyAlertNotifiedAt(long programId, long memberId) {
        return jdbc.queryForObject(
                "SELECT notified_at FROM program_vacancy_alert WHERE program_id = ? AND member_id = ?",
                LocalDateTime.class,
                programId,
                memberId);
    }

    public List<String> seatReleasedPayloads(long programId) {
        return jdbc.queryForList(
                "SELECT payload FROM outbox_event WHERE event_type = 'PROGRAM_SEAT_RELEASED'"
                        + " AND aggregate_type = 'PROGRAM' AND aggregate_id = ? ORDER BY id",
                String.class,
                programId);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
