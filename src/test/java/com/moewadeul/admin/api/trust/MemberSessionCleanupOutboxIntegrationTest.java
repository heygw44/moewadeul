package com.moewadeul.admin.api.trust;

import static com.moewadeul.admin.api.trust.AdminReportApiFixture.BASE;
import static com.moewadeul.admin.api.trust.AdminReportApiFixture.REPORTS;
import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.outbox.application.OutboxPublisher;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemberSessionCleanupOutboxIntegrationTest {

    private static final String ACTION =
            "{\"sanction\":{\"type\":\"PERMANENT\",\"reason\":\"사유\"},\"hideReview\":false}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberReportService memberReportService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @MockitoSpyBean
    private MemberSessions memberSessions;

    private AdminReportApiFixture api;

    @BeforeEach
    void setUp() {
        api = new AdminReportApiFixture(
                mvc,
                passwordEncoder,
                jdbc,
                new CompanionReviewFixture(jdbc, memberRepository),
                new MemberReportFixture(jdbc),
                memberReportService,
                withdrawalService);
    }

    @Test
    @DisplayName("[F-21] 정지 확정 직후 세션 삭제가 한 번 실패해도 응답은 200이고, outbox 처리기가 옛 세션을 마저 지운다")
    void reportActionFinishesSessionDeletionThroughOutbox() {
        long target = api.targetId;
        Cookie session = api.login(target);
        long reportId = api.insertReport("NO_SHOW", "IN_REVIEW", BASE);
        failFirstSessionDeletion();

        MvcTestResult response = api.post(REPORTS + "/" + reportId + "/action", ACTION);

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(api.memberStatus(target)).isEqualTo("SUSPENDED");
        assertThat(api.sanctionStatuses(target)).containsExactly("ACTIVE");
        assertThat(sessionRows(target)).isEqualTo(1);

        outboxPublisher.publishPending();

        assertOldSessionRejected(target, session);
    }

    @Test
    @DisplayName("[F-01] 탈퇴 직후 세션 삭제가 한 번 실패해도 탈퇴는 성공하고, outbox 처리기가 옛 세션을 마저 지운다")
    void withdrawalFinishesSessionDeletionThroughOutbox() {
        long target = api.targetId;
        Cookie session = api.login(target);
        failFirstSessionDeletion();

        api.withdraw(target);

        assertThat(api.memberStatus(target)).isEqualTo("WITHDRAWN");
        assertThat(sessionRows(target)).isEqualTo(1);

        outboxPublisher.publishPending();

        assertOldSessionRejected(target, session);
    }

    private void failFirstSessionDeletion() {
        doThrow(new IllegalStateException("session store down"))
                .doCallRealMethod()
                .when(memberSessions)
                .invalidateAll(anyLong());
    }

    private void assertOldSessionRejected(long memberId, Cookie session) {
        assertThat(sessionRows(memberId)).isZero();
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult write = mvc.patch()
                .uri("/api/me")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"바뀐닉네임\"}")
                .exchange();
        assertThat(write).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, memberId))
                .isNotEqualTo("바뀐닉네임");
    }

    private int sessionRows(long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class,
                String.valueOf(memberId));
    }
}
