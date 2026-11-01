package com.moewadeul.admin.api.trust;

import static com.moewadeul.admin.api.trust.AdminReportApiFixture.AUDIT_LOGS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.BASE;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.REPORTS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.SANCTIONS;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.json;
import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.report.ReportContentCleanupService;
import com.moewadeul.trust.application.sanction.SanctionFixture;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import com.moewadeul.trust.domain.sanction.SanctionType;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
class AdminReportQueryApiIntegrationTest {

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
    private ReportContentCleanupService reportContentCleanupService;

    @Autowired
    private WithdrawalService withdrawalService;

    private CompanionReviewFixture fixture;
    private MemberReportFixture reportFixture;
    private AdminReportApiFixture api;
    private SanctionFixture sanctionFixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        reportFixture = new MemberReportFixture(jdbc);
        api = new AdminReportApiFixture(
                mvc, passwordEncoder, jdbc, fixture, reportFixture, memberReportService, withdrawalService);
        sanctionFixture = new SanctionFixture(fixture, reportFixture, memberReportActionService, clock);
    }

    @Test
    @DisplayName("[F-21][SF-02] 신고 목록은 긴급 신고, 금전 요구 신고, 나머지 순이고 같은 묶음에서는 오래된 신고가 먼저다")
    void listIsOrderedByUrgencyMoneyThenAge() {
        long normal1 = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        long money1 = api.insertReport("MONEY_REQUEST", "RECEIVED", BASE.plus(Duration.ofHours(1)));
        long urgent1 = api.insertReport("HARASSMENT_OR_THREAT", "RECEIVED", BASE.plus(Duration.ofHours(2)));
        long normal2 = api.insertReport("OFFENSIVE_BEHAVIOR", "RECEIVED", BASE.plus(Duration.ofHours(3)));
        long urgent2 = api.insertReport("HARASSMENT_OR_THREAT", "RECEIVED", BASE.plus(Duration.ofHours(4)));
        long money2 = api.insertReport("MONEY_REQUEST", "RECEIVED", BASE.plus(Duration.ofHours(5)));

        MvcTestResult result = api.get(REPORTS);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(ids(result, "$.content[*].reportId"))
                .containsExactly(urgent1, urgent2, money1, money2, normal1, normal2);
        assertThat(result).bodyJson().extractingPath("$.content[0].kind").isEqualTo("MEMBER");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reporter.memberId")
                .isNotNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].target.nickname")
                .isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].content");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-21] 신고 목록은 status와 urgent 필터, 페이지를 적용하고 알 수 없는 status는 400이다")
    void listFiltersAndPages() {
        long received = api.insertReport("NO_SHOW", "RECEIVED", BASE);
        long inReview = api.insertReport("MONEY_REQUEST", "IN_REVIEW", BASE.plus(Duration.ofHours(1)));
        long urgent = api.insertReport("HARASSMENT_OR_THREAT", "IN_REVIEW", BASE.plus(Duration.ofHours(2)));

        assertThat(ids(api.get(REPORTS + "?status=IN_REVIEW"), "$.content[*].reportId"))
                .containsExactly(urgent, inReview);
        assertThat(ids(api.get(REPORTS + "?urgent=true"), "$.content[*].reportId"))
                .containsExactly(urgent);
        assertThat(ids(api.get(REPORTS + "?urgent=false"), "$.content[*].reportId"))
                .containsExactly(inReview, received);
        assertThat(ids(api.get(REPORTS + "?status=ACTIONED"), "$.content[*].reportId"))
                .isEmpty();
        MvcTestResult firstPage = api.get(REPORTS + "?size=2&page=0");
        MvcTestResult secondPage = api.get(REPORTS + "?size=2&page=1");
        assertThat(ids(firstPage, "$.content[*].reportId")).containsExactly(urgent, inReview);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(ids(secondPage, "$.content[*].reportId")).containsExactly(received);
        assertThat(secondPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        MvcTestResult invalid = api.get(REPORTS + "?status=NOPE");
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-21] 관리자가 아닌 회원은 관리자 API가 403이고, 세션이 없으면 401이다")
    void nonAdminCannotUseAdminApis() {
        Cookie userSession = api.login(api.reporterId);

        MvcTestResult forbidden = mvc.get().uri(REPORTS).cookie(userSession).exchange();
        MvcTestResult unauthorized = mvc.get().uri(AUDIT_LOGS).exchange();
        MvcTestResult forbiddenPost = mvc.post()
                .uri(SANCTIONS + "/1/lift")
                .cookie(userSession)
                .with(csrf())
                .exchange();

        assertThat(forbidden).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(forbiddenPost).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unauthorized).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[F-21][PV-07] 정리 작업이 내용을 지운 신고의 상세는 200이고 content, resultNote, 제재 이력의 reason이 null이다")
    void detailIsOkAfterContentCleanup() {
        long reportId = api.insertReport("NO_SHOW", "ACTIONED", BASE);
        jdbc.update(
                "UPDATE member_report SET handled_at = ?, result_note = '메모' WHERE id = ?",
                LocalDateTime.ofInstant(BASE, ZoneOffset.UTC),
                reportId);
        sanctionFixture.confirm(api.targetId, api.adminId, SanctionType.WARNING, "욕설");
        jdbc.update(
                "UPDATE sanction SET starts_at = ? WHERE member_id = ?",
                LocalDateTime.ofInstant(BASE, ZoneOffset.UTC),
                api.targetId);
        clock.advance(Duration.ofDays(400));

        reportContentCleanupService.clearExpiredContent();
        MvcTestResult result = api.get(REPORTS + "/" + reportId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").isNull();
        assertThat(result).bodyJson().extractingPath("$.resultNote").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sanctionHistory[0].reason")
                .isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sanctionHistory[0].type")
                .isEqualTo("WARNING");
    }

    @Test
    @DisplayName("[F-21] 신고 상세는 신고 내용, 베이스캠프, 신고자와 대상을 담고 회원 신고이면 companionReview가 null이다")
    void detailOfMemberReport() {
        long reportId = api.insertReport("NO_SHOW", "RECEIVED", BASE);

        MvcTestResult result = api.get(REPORTS + "/" + reportId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.reportId").isEqualTo((int) reportId);
        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("신고 내용 NO_SHOW");
        assertThat(result).bodyJson().extractingPath("$.basecamp.title").isEqualTo("굴업도 주말 1박");
        assertThat(result).bodyJson().extractingPath("$.basecamp.status").isEqualTo("COMPLETED");
        assertThat(result).bodyJson().extractingPath("$.basecamp.startDate").isEqualTo("2026-09-20");
        assertThat(result).bodyJson().extractingPath("$.companionReview").isNull();
        assertThat(result).bodyJson().extractingPath("$.handledBy").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sanctionHistory")
                .asList()
                .isEmpty();
    }

    @Test
    @DisplayName("[F-21][SN-07] 후기 신고 상세는 신고된 후기의 코멘트, 태그, 다시 동행 여부, 숨김 여부를 담는다")
    void detailOfReviewReport() {
        long reviewId = fixture.insertReview(api.basecampId, api.targetId, api.reporterId, true, BASE);
        fixture.insertTag(reviewId, "LATE");
        fixture.insertTag(reviewId, "CONSIDERATE");
        long reportId = reportFixture.insertReviewReport(
                api.reporterId, api.targetId, api.basecampId, reviewId, "IN_REVIEW", BASE);

        MvcTestResult result = api.get(REPORTS + "/" + reportId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("REVIEW");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.companionReview.comment")
                .isEqualTo("코멘트 " + api.targetId + "→" + api.reporterId);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.companionReview.tags")
                .asList()
                .containsExactly("CONSIDERATE", "LATE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.companionReview.rejoinWanted")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.companionReview.hidden").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-21] 없는 신고는 상세와 모든 처리가 404 NOT_FOUND이다")
    void unknownReportIsNotFound() {
        for (MvcTestResult result : List.of(
                api.get(REPORTS + "/999999"),
                api.post(REPORTS + "/999999/start-review", null),
                api.post(REPORTS + "/999999/action", "{\"hideReview\":true}"),
                api.post(REPORTS + "/999999/dismiss", null),
                api.lift(999999, "REVOKE"))) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        assertThat(api.count("admin_audit_log")).isZero();
    }

    private static List<Long> ids(MvcTestResult result, String path) {
        List<Number> numbers = json(result, path);
        return numbers.stream().map(Number::longValue).toList();
    }
}
