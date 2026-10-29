package com.moewadeul.admin.api.trust;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportCommand;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportType;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

final class AdminReportApiFixture {

    static final String REPORTS = "/api/admin/member-reports";
    static final String SANCTIONS = "/api/admin/sanctions";
    static final String AUDIT_LOGS = "/api/admin/audit-logs";
    static final String SECRET_REASON = "비밀사유가들어간문장";
    static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final String PASSWORD = "Valid-pass1";
    private static final String REQUEST_IP = "203.0.113.7";
    private static final String SESSION_COOKIE = "SESSION";

    final long adminId;
    final long reporterId;
    final long targetId;
    final long basecampId;
    private final Cookie adminSession;
    private final MockMvcTester mvc;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final CompanionReviewFixture fixture;
    private final MemberReportFixture reportFixture;
    private final MemberReportService memberReportService;
    private final WithdrawalService withdrawalService;

    AdminReportApiFixture(
            MockMvcTester mvc,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc,
            CompanionReviewFixture fixture,
            MemberReportFixture reportFixture,
            MemberReportService memberReportService,
            WithdrawalService withdrawalService) {
        this.mvc = mvc;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.fixture = fixture;
        this.reportFixture = reportFixture;
        this.memberReportService = memberReportService;
        this.withdrawalService = withdrawalService;
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
        basecampId = fixture.saveBasecamp("COMPLETED", BASE);
        reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    long insertReport(String type, String status, Instant createdAt) {
        long newReporter = fixture.saveVerifiedMember(TestSequence.nickname());
        return reportFixture.insertMemberReport(newReporter, targetId, basecampId, type, status, createdAt);
    }

    long urgentReport(long target) {
        long newReporter = fixture.saveVerifiedMember(TestSequence.nickname());
        long newBasecamp = fixture.saveBasecamp("COMPLETED", BASE);
        fixture.insertMember(newBasecamp, newReporter, "MEMBER", "ACTIVE");
        fixture.insertMember(newBasecamp, target, "MEMBER", "ACTIVE");
        return memberReportService.report(
                newReporter,
                new MemberReportCommand(
                        target, newBasecamp, ReportKind.MEMBER, null, ReportType.HARASSMENT_OR_THREAT, "위협했다"));
    }

    void withdraw(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        withdrawalService.withdraw(memberId, PASSWORD, REQUEST_IP);
    }

    MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(adminSession).exchange();
    }

    MvcTestResult lift(long sanctionId, String reason) {
        return post(SANCTIONS + "/" + sanctionId + "/lift", "{\"reason\":\"" + reason + "\"}");
    }

    MvcTestResult post(String uri, String body) {
        var request = mvc.post().uri(uri).cookie(adminSession).with(csrf());
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    Map<String, Object> resolvedPayload() {
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'MEMBER_REPORT_RESOLVED'", String.class);
        return JsonPath.read(payload, "$");
    }

    void assertAuditRow(String action, long reportId) {
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(admin_id, ':', target_type, ':', target_id) FROM admin_audit_log"
                                + " WHERE action = ?",
                        String.class,
                        action))
                .isEqualTo(adminId + ":MEMBER_REPORT:" + reportId);
    }

    int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    String memberStatus(long memberId) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId);
    }

    Instant suspendedUntil(long memberId) {
        return jdbc.queryForObject(
                                "SELECT suspended_until FROM member WHERE id = ?", java.sql.Timestamp.class, memberId)
                        == null
                ? null
                : jdbc.queryForObject(
                                "SELECT suspended_until FROM member WHERE id = ?",
                                java.time.LocalDateTime.class,
                                memberId)
                        .toInstant(java.time.ZoneOffset.UTC);
    }

    List<String> sanctionStatuses(long memberId) {
        return jdbc.queryForList("SELECT status FROM sanction WHERE member_id = ? ORDER BY id", String.class, memberId);
    }

    long sanctionOfReport(long reportId) {
        return jdbc.queryForObject("SELECT id FROM sanction WHERE report_id = ?", Long.class, reportId);
    }

    String sanctionStatus(long sanctionId) {
        return jdbc.queryForObject("SELECT status FROM sanction WHERE id = ?", String.class, sanctionId);
    }

    Cookie login(long memberId) {
        MvcTestResult result = loginRequest(memberId);
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    MvcTestResult loginRequest(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
