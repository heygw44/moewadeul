package com.moewadeul.admin.api.trust;

import static com.moewadeul.admin.api.trust.AdminReportApiFixture.BASE;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.REPORTS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.SECRET_REASON;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.body;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.outbox.application.OutboxPublisher;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.companion.CompanionReviewQueryService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.sanction.SanctionExpiryService;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
class AdminReportActionApiIntegrationTest {

    private static final String SECRET_NOTE = "비밀메모가들어간문장";

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
    private CompanionReviewQueryService companionReviewQueryService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private SanctionExpiryService sanctionExpiryService;

    @Autowired
    private WithdrawalService withdrawalService;

    private CompanionReviewFixture fixture;
    private MemberReportFixture reportFixture;
    private AdminReportApiFixture api;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        reportFixture = new MemberReportFixture(jdbc);
        api = new AdminReportApiFixture(
                mvc, passwordEncoder, jdbc, fixture, reportFixture, memberReportService, withdrawalService);
    }

    @Test
    @DisplayName("[F-21][SN-05] 조치한 신고의 임시 정지는 만료된 뒤에도 EXPIRED로 남아 제재 이력으로 센다")
    void actionedReportKeepsExpiredTemporarySuspension() {
        long target = fixture.saveVerifiedMember(TestSequence.nickname());
        long reportId = api.urgentReport(target);
        api.post(REPORTS + "/" + reportId + "/start-review", null);
        api.post(REPORTS + "/" + reportId + "/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"위협 발언\"}}");
        clock.advance(Duration.ofHours(73));
        sanctionExpiryService.run();

        api.withdraw(target);

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM sanction WHERE report_id = ? AND type = 'TEMPORARY_72H'",
                        String.class,
                        reportId))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                        "SELECT ci_retained_until FROM identity_verification WHERE member_id = ?",
                        LocalDateTime.class,
                        target))
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(365)), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("[SN-04] 접수 상태에서는 조치와 기각이 409이고, 검토 시작은 한 번만 되며, 처리한 신고는 다시 처리할 수 없다")
    void stateTransitionsAreEnforced() {
        long reportId = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        String path = REPORTS + "/" + reportId;

        assertThat(api.post(path + "/action", "{\"hideReview\":true,\"sanction\":null}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(api.post(path + "/dismiss", null)).hasStatus(HttpStatus.CONFLICT);
        MvcTestResult started = api.post(path + "/start-review", null);
        assertThat(started).hasStatus(HttpStatus.OK);
        assertThat(started).bodyJson().extractingPath("$.status").isEqualTo("IN_REVIEW");
        MvcTestResult startedTwice = api.post(path + "/start-review", null);
        assertThat(startedTwice).hasStatus(HttpStatus.CONFLICT);
        assertThat(startedTwice).bodyJson().extractingPath("$.code").isEqualTo("REPORT_INVALID_STATE");
        assertThat(api.post(path + "/dismiss", "{\"note\":\"근거 없음\"}")).hasStatus(HttpStatus.OK);
        assertThat(api.post(path + "/dismiss", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.post(path + "/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"사유\"}}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(api.post(path + "/start-review", null)).hasStatus(HttpStatus.CONFLICT);

        assertThat(reportStatus(reportId)).isEqualTo("DISMISSED");
        assertThat(api.count("sanction")).isZero();
        assertThat(api.count("admin_audit_log")).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-04][SN-12] 정지를 조치하면 신고가 ACTIONED가 되고 대상의 기존 세션이 401이며 베이스캠프 정리와 결과 이벤트가 기록된다")
    void actionWithSuspensionInvalidatesSessionAndRecords() {
        Cookie session = api.login(api.targetId);
        Cookie secondSession = api.login(api.targetId);
        long ledBasecamp = ledBasecamp(api.targetId);
        long reportId = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        api.post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = api.post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"PERMANENT\",\"reason\":\"" + SECRET_REASON + "\"},\"hideReview\":false,"
                        + "\"note\":\"" + SECRET_NOTE + "\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIONED");
        long sanctionId = ((Number) json(result, "$.sanctionId")).longValue();
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/me").cookie(secondSession).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionCount(api.targetId)).isZero();
        assertThat(api.memberStatus(api.targetId)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', handled_by, ':', result_note) FROM member_report WHERE id = ?",
                        String.class,
                        reportId))
                .isEqualTo("ACTIONED:" + api.adminId + ":" + SECRET_NOTE);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(type, ':', level, ':', report_id, ':', created_by, ':', status)"
                                + " FROM sanction WHERE id = ?",
                        String.class,
                        sanctionId))
                .isEqualTo("PERMANENT:4:" + reportId + ":" + api.adminId + ":ACTIVE");
        assertThat(api.count("outbox_event WHERE event_type = 'SANCTION_BASECAMP_CLEANUP'"))
                .isEqualTo(1);
        assertThat(api.count("outbox_event WHERE event_type = 'SANCTION_PROGRAM_CLEANUP'"))
                .isEqualTo(1);
        Map<String, Object> payload = api.resolvedPayload();
        assertThat(payload).containsOnlyKeys("reportId", "reporterId", "result");
        assertThat(payload.get("result")).isEqualTo("ACTIONED");
        assertThat(((Number) payload.get("reportId")).longValue()).isEqualTo(reportId);
        assertThat(((Number) payload.get("reporterId")).longValue())
                .isEqualTo(jdbc.queryForObject(
                        "SELECT reporter_id FROM member_report WHERE id = ?", Long.class, reportId));
        api.assertAuditRow("REPORT_ACTION", reportId);
        assertThat(api.count("admin_audit_log WHERE action = 'REPORT_ACTION'")).isEqualTo(1);
        assertThat(allAuditDetails()).doesNotContain(SECRET_REASON, SECRET_NOTE);

        outboxPublisher.publishPending();

        assertThat(basecampStatus(ledBasecamp)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("[SN-10] 경고를 조치하면 대상의 세션이 유지되고 이용 정지 이벤트는 기록하지 않는다")
    void actionWithWarningKeepsSession() {
        Cookie session = api.login(api.targetId);
        long reportId = api.insertReport("OFFENSIVE_BEHAVIOR", "IN_REVIEW", BASE);

        MvcTestResult result = api.post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"욕설\"},\"hideReview\":false}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.OK);
        assertThat(api.sanctionStatuses(api.targetId)).containsExactly("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT level FROM sanction WHERE member_id = ?", Integer.class, api.targetId))
                .isEqualTo(1);
        assertThat(api.count("outbox_event WHERE event_type = 'SANCTION_BASECAMP_CLEANUP'"))
                .isZero();
        assertThat(api.count("outbox_event WHERE event_type = 'MEMBER_REPORT_RESOLVED'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[SN-10] 계산된 다음 단계와 다른 제재를 요청하면 400이고 신고 상태, 제재, 이벤트, 감사 로그가 그대로다")
    void levelMismatchChangesNothing() {
        long reportId = api.insertReport("NO_SHOW", "IN_REVIEW", BASE);

        MvcTestResult result = api.post(
                REPORTS + "/" + reportId + "/action", "{\"sanction\":{\"type\":\"SUSPEND_7D\",\"reason\":\"단계 건너뜀\"}}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(reportStatus(reportId)).isEqualTo("IN_REVIEW");
        assertThat(api.count("sanction")).isZero();
        assertThat(api.count("outbox_event")).isZero();
        assertThat(api.count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[SN-04] 제재도 후기 숨김도 없거나, 회원 신고에 후기 숨김을 요청하거나, 제재 종류가 올바르지 않으면 400이다")
    void invalidActionRequests() {
        long reportId = api.insertReport("NO_SHOW", "IN_REVIEW", BASE);
        String path = REPORTS + "/" + reportId + "/action";

        for (String body : List.of(
                "{\"sanction\":null,\"hideReview\":false}",
                "{}",
                "{\"hideReview\":true}",
                "{\"sanction\":{\"type\":\"TEMPORARY_72H\",\"reason\":\"사유\"}}",
                "{\"sanction\":{\"type\":\"NOPE\",\"reason\":\"사유\"}}")) {
            MvcTestResult result = api.post(path, body);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(reportStatus(reportId)).isEqualTo("IN_REVIEW");
        assertThat(api.count("sanction")).isZero();
        assertThat(api.count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[SN-04] 요청 형식 오류는 상태 검사보다 먼저이고, 상태 오류는 요청 내용 검사보다 먼저다")
    void validationOrder() {
        long received = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        long other = api.insertReport("MONEY_REQUEST", "RECEIVED", BASE);

        MvcTestResult unknownType =
                api.post(REPORTS + "/" + received + "/action", "{\"sanction\":{\"type\":\"NOPE\",\"reason\":\"사유\"}}");
        MvcTestResult emptyRequestOnWrongState = api.post(REPORTS + "/" + other + "/action", "{\"hideReview\":false}");

        assertThat(unknownType).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(emptyRequestOnWrongState).hasStatus(HttpStatus.CONFLICT);
        assertThat(emptyRequestOnWrongState).bodyJson().extractingPath("$.code").isEqualTo("REPORT_INVALID_STATE");
    }

    @Test
    @DisplayName("[SN-07] 후기 신고를 조치하며 후기를 숨기면 hidden_at이 채워지고 받은 후기 목록에서 빠진다")
    void hideReviewRemovesItFromReceivedList() {
        long reviewId = fixture.insertReview(api.basecampId, api.targetId, api.reporterId, true, BASE);
        long reportId = reportFixture.insertReviewReport(
                api.reporterId, api.targetId, api.basecampId, reviewId, "IN_REVIEW", BASE);
        assertThat(companionReviewQueryService.received(api.reporterId, 0, 20).content())
                .hasSize(1);

        MvcTestResult result =
                api.post(REPORTS + "/" + reportId + "/action", "{\"sanction\":null,\"hideReview\":true}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.sanctionId").isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT hidden_at IS NOT NULL FROM companion_review WHERE id = ?", Boolean.class, reviewId))
                .isTrue();
        assertThat(companionReviewQueryService.received(api.reporterId, 0, 20).content())
                .isEmpty();
        assertThat(api.count("sanction")).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'REPORT_ACTION'", String.class))
                .contains("\"hideReview\": true")
                .doesNotContain("sanctionId");
    }

    @Test
    @DisplayName("[SN-07] 후기 신고를 조치하며 후기 숨김과 경고를 함께 요청하면 둘 다 적용한다")
    void hideReviewAndWarningTogether() {
        long reviewId = fixture.insertReview(api.basecampId, api.targetId, api.reporterId, false, BASE);
        long reportId = reportFixture.insertReviewReport(
                api.reporterId, api.targetId, api.basecampId, reviewId, "IN_REVIEW", BASE);

        MvcTestResult result = api.post(
                REPORTS + "/" + reportId + "/action",
                "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"부적절한 후기\"},\"hideReview\":true}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(api.sanctionStatuses(api.targetId)).containsExactly("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT hidden_at IS NOT NULL FROM companion_review WHERE id = ?", Boolean.class, reviewId))
                .isTrue();
    }

    @Test
    @DisplayName("[SN-05] 긴급 신고를 조치하면 그 신고의 임시 정지는 해제하지 않고 그대로 둔다")
    void actionLeavesTemporarySuspensionAlone() {
        long reportId = api.urgentReport(api.targetId);
        api.post(REPORTS + "/" + reportId + "/start-review", null);

        MvcTestResult result = api.post(
                REPORTS + "/" + reportId + "/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"위협 발언\"}}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM sanction WHERE report_id = ? AND type = 'TEMPORARY_72H'",
                        String.class,
                        reportId))
                .isEqualTo("ACTIVE");
        assertThat(api.memberStatus(api.targetId)).isEqualTo("SUSPENDED");
    }

    private long ledBasecamp(long leaderId) {
        long ledBasecampId = fixture.saveBasecamp("RECRUITING", null);
        jdbc.update(
                "UPDATE basecamp_member SET member_id = ? WHERE basecamp_id = ? AND role = 'LEADER'",
                leaderId,
                ledBasecampId);
        jdbc.update("UPDATE basecamp SET leader_id = ? WHERE id = ?", leaderId, ledBasecampId);
        return ledBasecampId;
    }

    private String allAuditDetails() {
        return String.join("\n", jdbc.queryForList("SELECT IFNULL(detail, '') FROM admin_audit_log", String.class));
    }

    private String reportStatus(long reportId) {
        return jdbc.queryForObject("SELECT status FROM member_report WHERE id = ?", String.class, reportId);
    }

    private String basecampStatus(long basecampId) {
        return jdbc.queryForObject("SELECT status FROM basecamp WHERE id = ?", String.class, basecampId);
    }

    private int sessionCount(long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class,
                String.valueOf(memberId));
    }
}
