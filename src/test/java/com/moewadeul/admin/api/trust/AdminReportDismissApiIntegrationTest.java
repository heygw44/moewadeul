package com.moewadeul.admin.api.trust;

import static com.moewadeul.admin.api.trust.AdminReportApiFixture.BASE;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.REPORTS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportCommand;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.sanction.SanctionExpiryService;
import com.moewadeul.trust.application.sanction.SanctionFixture;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportType;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class AdminReportDismissApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MemberReportService memberReportService;

    @Autowired
    private MemberReportActionService memberReportActionService;

    @Autowired
    private SanctionExpiryService sanctionExpiryService;

    @Autowired
    private WithdrawalService withdrawalService;

    private CompanionReviewFixture fixture;
    private AdminReportApiFixture api;
    private SanctionFixture sanctionFixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        MemberReportFixture reportFixture = new MemberReportFixture(jdbc);
        api = new AdminReportApiFixture(
                mvc, passwordEncoder, jdbc, fixture, reportFixture, memberReportService, withdrawalService);
        sanctionFixture = new SanctionFixture(fixture, reportFixture, memberReportActionService, clock);
    }

    @Test
    @DisplayName("[F-21][SN-05] 신고 상세는 기각된 신고의 임시 정지를 제재 이력에서 빼고, 조치한 신고의 임시 정지는 보여 준다")
    void detailHidesTemporarySuspensionOfDismissedReport() {
        long dismissedTarget = fixture.saveVerifiedMember(TestSequence.nickname());
        long dismissedReport = api.urgentReport(dismissedTarget);
        api.post(REPORTS + "/" + dismissedReport + "/start-review", null);
        api.post(REPORTS + "/" + dismissedReport + "/dismiss", null);
        long actionedReport = api.urgentReport(dismissedTarget);
        api.post(REPORTS + "/" + actionedReport + "/start-review", null);
        sanctionFixture.confirm(dismissedTarget, api.adminId, SanctionType.WARNING, "욕설");

        MvcTestResult dismissedDetail = api.get(REPORTS + "/" + dismissedReport);
        MvcTestResult actionedDetail = api.get(REPORTS + "/" + actionedReport);

        assertThat(dismissedDetail).hasStatus(HttpStatus.OK);
        List<String> types = json(dismissedDetail, "$.sanctionHistory[*].type");
        assertThat(types).containsExactly("WARNING", "TEMPORARY_72H");
        List<String> statuses = json(dismissedDetail, "$.sanctionHistory[*].status");
        assertThat(statuses).containsExactly("ACTIVE", "ACTIVE");
        assertThat(actionedDetail).bodyJson().extractingPath("$.status").isEqualTo("IN_REVIEW");
        assertThat(dismissedDetail).bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
    }

    @Test
    @DisplayName("[F-21][SN-05] 기각된 임시 정지는 REVOKED가 되고 제재 이력의 임시 정지 항목이 없다")
    void dismissedTemporarySuspensionIsNotInHistory() {
        long dismissedTarget = fixture.saveVerifiedMember(TestSequence.nickname());
        long reportId = api.urgentReport(dismissedTarget);
        api.post(REPORTS + "/" + reportId + "/start-review", null);
        api.post(REPORTS + "/" + reportId + "/dismiss", null);

        MvcTestResult detail = api.get(REPORTS + "/" + reportId);

        assertThat(api.sanctionStatuses(dismissedTarget)).containsExactly("REVOKED");
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat((List<?>) json(detail, "$.sanctionHistory")).isEmpty();
    }

    @Test
    @DisplayName("[F-21][SN-05][PV-05] 72시간이 지나 만료된 임시 정지의 신고를 기각해도 REVOKED가 되어, 탈퇴할 때 제재 이력으로 세지 않고 CI 해시를 30일만 남긴다")
    void dismissingAfterTemporarySuspensionExpiredLiftsItAndKeepsCiForThirtyDays() {
        long dismissedTarget = fixture.saveVerifiedMember(TestSequence.nickname());
        long reportId = api.urgentReport(dismissedTarget);
        api.post(REPORTS + "/" + reportId + "/start-review", null);
        clock.advance(Duration.ofHours(73));
        sanctionExpiryService.run();
        assertThat(api.sanctionStatuses(dismissedTarget)).containsExactly("EXPIRED");

        MvcTestResult dismissed = api.post(REPORTS + "/" + reportId + "/dismiss", null);
        api.withdraw(dismissedTarget);

        assertThat(dismissed).hasStatus(HttpStatus.OK);
        assertThat(api.sanctionStatuses(dismissedTarget)).containsExactly("REVOKED");
        assertThat(jdbc.queryForObject(
                        "SELECT ci_retained_until FROM identity_verification WHERE member_id = ?",
                        LocalDateTime.class,
                        dismissedTarget))
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(30)), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("[SN-05] 긴급 신고를 기각하면 임시 정지가 REVOKED가 되고 미인증 회원은 UNVERIFIED로 돌아가며 다음 제재 단계는 1단계다")
    void dismissUrgentReportReleasesUnverifiedMember() {
        long reportId = api.urgentReport(api.targetId);
        assertThat(api.memberStatus(api.targetId)).isEqualTo("SUSPENDED");
        api.post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = api.post(REPORTS + "/" + reportId + "/dismiss", "{\"note\":\"오해였음\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
        assertThat(api.sanctionStatuses(api.targetId)).containsExactly("REVOKED");
        assertThat(jdbc.queryForObject("SELECT lifted_by FROM sanction WHERE member_id = ?", Long.class, api.targetId))
                .isEqualTo(api.adminId);
        assertThat(api.memberStatus(api.targetId)).isEqualTo("UNVERIFIED");
        assertThat(jdbc.queryForObject("SELECT suspended_until FROM member WHERE id = ?", Object.class, api.targetId))
                .isNull();
        assertThat(api.resolvedPayload().get("result")).isEqualTo("DISMISSED");
        api.assertAuditRow("REPORT_DISMISS", reportId);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'REPORT_DISMISS'", String.class))
                .contains("liftedSanctionId");
        sanctionFixture.confirm(api.targetId, api.adminId, SanctionType.WARNING, "경고");
        assertThat(jdbc.queryForObject("SELECT level FROM sanction WHERE type = 'WARNING'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[SN-05] 이메일 인증을 마친 회원은 긴급 신고가 기각되면 ACTIVE로 돌아간다")
    void dismissUrgentReportReleasesVerifiedMemberToActive() {
        jdbc.update("UPDATE member SET email_verified_at = NOW(6), status = 'ACTIVE' WHERE id = ?", api.targetId);
        long reportId = api.urgentReport(api.targetId);
        api.post(REPORTS + "/" + reportId + "/start-review", null);

        api.post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(api.memberStatus(api.targetId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[SN-05] 같은 대상의 긴급 신고 2건 중 나중 신고를 기각하면 정지는 유지되고 종료 시각은 남은 임시 정지로 줄어든다")
    void dismissOneOfTwoUrgentReportsKeepsSuspension() {
        long first = api.urgentReport(api.targetId);
        Instant firstEnd = sanctionEnd(first);
        clock.advance(Duration.ofHours(1));
        long second = api.urgentReport(api.targetId);
        assertThat(sanctionEnd(second)).isAfter(firstEnd);
        api.post(REPORTS + "/" + second + "/start-review", null);

        api.post(REPORTS + "/" + second + "/dismiss", null);

        assertThat(api.memberStatus(api.targetId)).isEqualTo("SUSPENDED");
        assertThat(api.suspendedUntil(api.targetId)).isEqualTo(firstEnd);
        assertThat(api.sanctionStatus(api.sanctionOfReport(first))).isEqualTo("ACTIVE");
        assertThat(api.sanctionStatus(api.sanctionOfReport(second))).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("[SN-05] 확정된 영구 정지가 남은 회원은 긴급 신고를 기각해도 정지를 유지한다")
    void dismissKeepsConfirmedSuspension() {
        sanctionFixture.confirm(api.targetId, api.adminId, SanctionType.PERMANENT, "심각한 위반");
        long reportId = api.urgentReport(api.targetId);
        api.post(REPORTS + "/" + reportId + "/start-review", null);

        api.post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(api.memberStatus(api.targetId)).isEqualTo("SUSPENDED");
        assertThat(api.suspendedUntil(api.targetId)).isNull();
        assertThat(api.sanctionStatus(api.sanctionOfReport(reportId))).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("[SN-05] 긴급이 아닌 신고를 기각하면 대상 회원의 상태를 건드리지 않는다")
    void dismissNonUrgentReportLeavesMemberAlone() {
        sanctionFixture.confirm(api.targetId, api.adminId, SanctionType.PERMANENT, "반복");
        String statusBefore = api.memberStatus(api.targetId);
        long reportId = api.insertReport("NO_SHOW", "IN_REVIEW", BASE);

        api.post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(api.memberStatus(api.targetId)).isEqualTo(statusBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'REPORT_DISMISS'", String.class))
                .doesNotContain("liftedSanctionId");
    }

    @Test
    @DisplayName("[SN-05] 신청만 한 신고자의 긴급 신고는 임시 정지가 없어서, 기각해도 대상 회원의 상태를 건드리지 않고 기각된다")
    void dismissUrgentReportWithoutSuspension() {
        long applicant = fixture.saveVerifiedMember(TestSequence.nickname());
        long newBasecamp = fixture.saveBasecamp("COMPLETED", BASE);
        fixture.insertApplication(newBasecamp, applicant, "CANCELED");
        fixture.insertMember(newBasecamp, api.targetId, "MEMBER", "ACTIVE");
        String statusBefore = api.memberStatus(api.targetId);
        long reportId = memberReportService.report(
                applicant,
                new MemberReportCommand(
                        api.targetId, newBasecamp, ReportKind.MEMBER, null, ReportType.HARASSMENT_OR_THREAT, "위협했다"));
        api.post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = api.post(REPORTS + "/" + reportId + "/dismiss", null);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status FROM member_report WHERE id = ?", String.class, reportId))
                .isEqualTo("DISMISSED");
        assertThat(api.memberStatus(api.targetId)).isEqualTo(statusBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanction WHERE report_id = ?", Integer.class, reportId))
                .isZero();
    }

    private Instant sanctionEnd(long reportId) {
        return jdbc.queryForObject(
                        "SELECT ends_at FROM sanction WHERE report_id = ?", java.time.LocalDateTime.class, reportId)
                .toInstant(java.time.ZoneOffset.UTC);
    }
}
