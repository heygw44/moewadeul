package com.moewadeul.basecamp.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class BasecampApiFixture {

    public static final String VALID_PASSWORD = "Valid-pass1";
    public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2026, 10, 20);
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final int ADULT_BIRTH_YEAR = 2007;
    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    private final MockMvcTester mvc;
    private final MemberJpaRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationIssuer emailVerificationIssuer;
    private final JdbcTemplate jdbc;
    private int identitySequence;

    public BasecampApiFixture(
            MockMvcTester mvc,
            MemberJpaRepository memberRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationIssuer emailVerificationIssuer,
            JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationIssuer = emailVerificationIssuer;
        this.jdbc = jdbc;
    }

    public Member saveMember() {
        return memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(VALID_PASSWORD)).build());
    }

    public Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    public Cookie verifiedSession(Member member) {
        Cookie session = login(member);
        String code = emailVerificationIssuer
                .issueFor(member.getId(), LOCAL_IP, TestSequence.next())
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        return session;
    }

    public Cookie identityVerifiedSession(Member member, String gender) {
        Cookie session = verifiedSession(member);
        identitySequence++;
        MvcTestResult result = mvc.post()
                .uri("/api/me/identity-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":%d,\"gender\":\"%s\",\"demoIdentityKey\":\"demo-member-%d\"}"
                        .formatted(ADULT_BIRTH_YEAR, gender, identitySequence))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return session;
    }

    public long insertSpot(String name, double lat, double lng) {
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name,
                "POINT(%s %s)".formatted(lat, lng));
        return jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    public void setSpotStatus(long spotId, String status) {
        jdbc.update("UPDATE spot SET status = ?, version = version + 1 WHERE id = ?", status, spotId);
    }

    public long insertBasecamp(Member leader, long spotId, String status, LocalDate startDate) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '굴업도 주말 1박', '함께 가요', ?, ?, 4, ?, ?, ?)",
                leader.getId(),
                spotId,
                startDate,
                startDate.plusDays(2),
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMemberRow(id, leader, "LEADER", "ACTIVE");
        return id;
    }

    public void insertMemberRow(long basecampId, Member member, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                basecampId,
                member.getId(),
                role,
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }

    public void insertApplicationRow(long basecampId, Member applicant, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, applied_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                basecampId,
                applicant.getId(),
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }

    public void setClosedReason(long basecampId, String status, String closedReason) {
        jdbc.update("UPDATE basecamp SET status = ?, closed_reason = ? WHERE id = ?", status, closedReason, basecampId);
    }

    public void setStatus(long basecampId, String status) {
        jdbc.update("UPDATE basecamp SET status = ? WHERE id = ?", status, basecampId);
    }

    public void setContactInfo(long basecampId, String contactInfo) {
        jdbc.update("UPDATE basecamp SET contact_info = ? WHERE id = ?", contactInfo, basecampId);
    }

    public void setCapacity(long basecampId, int capacity) {
        jdbc.update("UPDATE basecamp SET capacity = ? WHERE id = ?", capacity, basecampId);
    }

    public List<Map<String, Object>> outboxEvents(String eventType) {
        return jdbc.queryForList(
                "SELECT aggregate_type, aggregate_id, payload FROM outbox_event WHERE event_type = ? ORDER BY id",
                eventType);
    }

    public int outboxCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    public Map<String, Object> basecampRow(long basecampId) {
        return jdbc.queryForMap("SELECT * FROM basecamp WHERE id = ?", basecampId);
    }

    public String applicationStatus(long basecampId, Member applicant) {
        return jdbc.queryForObject(
                "SELECT status FROM basecamp_application WHERE basecamp_id = ? AND applicant_id = ?",
                String.class,
                basecampId,
                applicant.getId());
    }
}
