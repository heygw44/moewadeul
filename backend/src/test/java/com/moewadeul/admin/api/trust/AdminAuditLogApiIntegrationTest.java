package com.moewadeul.admin.api.trust;

import static com.moewadeul.admin.api.trust.AdminReportApiFixture.AUDIT_LOGS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.BASE;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.REPORTS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.SECRET_REASON;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.body;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import java.time.Instant;
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
class AdminAuditLogApiIntegrationTest {

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
    private WithdrawalService withdrawalService;

    private AdminReportApiFixture api;

    @BeforeEach
    void setUp() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        MemberReportFixture reportFixture = new MemberReportFixture(jdbc);
        api = new AdminReportApiFixture(
                mvc, passwordEncoder, jdbc, fixture, reportFixture, memberReportService, withdrawalService);
    }

    @Test
    @DisplayName("[F-21] 감사 로그를 최근 기록부터 돌려주고 adminId, targetType, 기간 필터와 페이지를 적용한다")
    void auditLogsAreFilteredAndPaged() {
        long reportId = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        api.post(REPORTS + "/" + reportId + "/start-review", null);
        api.post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"" + SECRET_REASON + "\"}}");
        long sanctionId = api.sanctionOfReport(reportId);
        api.lift(sanctionId, "REVOKE");

        MvcTestResult all = api.get(AUDIT_LOGS);

        assertThat(all).hasStatus(HttpStatus.OK);
        assertThat((List<String>) json(all, "$.content[*].action"))
                .containsExactly("SANCTION_LIFT", "REPORT_ACTION", "REPORT_START_REVIEW");
        assertThat(all).bodyJson().extractingPath("$.content[0].adminId").isEqualTo((int) api.adminId);
        assertThat(all).bodyJson().extractingPath("$.content[0].targetType").isEqualTo("SANCTION");
        assertThat(all).bodyJson().extractingPath("$.content[0].targetId").isEqualTo((int) sanctionId);
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[1].detail.sanctionType")
                .isEqualTo("WARNING");
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[2].detail.toStatus")
                .isEqualTo("IN_REVIEW");
        assertThat(body(all)).doesNotContain(SECRET_REASON);
        assertThat((List<String>) json(api.get(AUDIT_LOGS + "?targetType=MEMBER_REPORT"), "$.content[*].action"))
                .containsExactly("REPORT_ACTION", "REPORT_START_REVIEW");
        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?adminId=" + api.reporterId), "$.content"))
                .isEmpty();
        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?adminId=" + api.adminId), "$.content"))
                .hasSize(3);
        MvcTestResult firstPage = api.get(AUDIT_LOGS + "?size=2");
        assertThat((List<?>) json(firstPage, "$.content")).hasSize(2);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?size=2&page=1"), "$.content"))
                .hasSize(1);
    }

    @Test
    @DisplayName("[F-21] 감사 로그의 기간 필터는 from 이상 to 미만이다")
    void auditLogPeriodBoundaries() {
        long reportId = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        api.post(REPORTS + "/" + reportId + "/start-review", null);
        Instant recorded = clock.instant();

        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?from=" + recorded), "$.content"))
                .hasSize(1);
        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?from=" + recorded.plusSeconds(1)), "$.content"))
                .isEmpty();
        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?to=" + recorded), "$.content"))
                .isEmpty();
        assertThat((List<?>) json(api.get(AUDIT_LOGS + "?to=" + recorded.plusSeconds(1)), "$.content"))
                .hasSize(1);
        MvcTestResult invalid = api.get(AUDIT_LOGS + "?from=yesterday");
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }
}
