package com.moewadeul.member.api.login;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.application.login.MemberLoginService;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.password.PasswordResetToken;
import com.moewadeul.member.domain.password.PasswordResetTokenRepository;
import com.moewadeul.member.domain.password.ResetToken;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class LoginSessionCommitRaceIntegrationTest {

    private static final String OLD_PASSWORD = "Valid-pass1";
    private static final String NEW_PASSWORD = "Newer-pass2";
    private static final String SESSION_COOKIE = "SESSION";
    private static final long WAIT_SECONDS = 60;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @MockitoSpyBean
    private MemberLoginService memberLoginService;

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pauseArmed = new AtomicBoolean();

    @AfterEach
    void releasePausedRequest() {
        resume.countDown();
    }

    @Test
    @DisplayName("[PW-02] 컨트롤러가 회원을 확인한 뒤 세션이 저장되기 전에 재설정이 끝나도, 저장된 세션은 지워지고 그 쿠키는 401이다")
    void resetBetweenControllerCheckAndSessionSaveLeavesNoSession() throws Exception {
        Member member = memberFixture.saveWithPassword(OLD_PASSWORD);

        MvcTestResult login = loginWhileOtherWorkRuns(member, () -> {
            MvcTestResult confirm = postJson("/api/auth/password-reset/confirm", confirmBody(issueToken(member)));
            assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
        });

        assertThat(sessionCount(member)).isZero();
        Cookie session = login.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[SN-12] 컨트롤러가 회원을 확인한 뒤 세션이 저장되기 전에 이용 정지가 확정돼도, 저장된 세션은 지워진다")
    void suspensionBetweenControllerCheckAndSessionSaveLeavesNoSession() throws Exception {
        Member member = memberFixture.saveWithPassword(OLD_PASSWORD);

        loginWhileOtherWorkRuns(
                member,
                () -> jdbc.update(
                        "UPDATE member SET status = 'SUSPENDED', suspended_until = NULL WHERE id = ?", member.getId()));

        assertThat(sessionCount(member)).isZero();
    }

    private MvcTestResult loginWhileOtherWorkRuns(Member member, Runnable otherWork) throws Exception {
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    if (pauseArmed.compareAndSet(true, false)) {
                        paused.countDown();
                        assertThat(resume.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                    }
                    return null;
                })
                .when(memberLoginService)
                .ensureStillLoginable(any());
        pauseArmed.set(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcTestResult> login = executor.submit(() -> postJson(
                    "/api/auth/login",
                    "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), OLD_PASSWORD)));
            assertThat(paused.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            otherWork.run();
            resume.countDown();

            MvcTestResult result = login.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(result).hasStatus(HttpStatus.OK);
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private String issueToken(Member member) {
        ResetToken token = ResetToken.generate(new SecureRandom());
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(token.value()), clock.instant()));
        return token.value();
    }

    private MvcTestResult postJson(String path, String body) {
        return mvc.post()
                .uri(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String confirmBody(String token) {
        return "{\"token\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                .formatted(token, NEW_PASSWORD, NEW_PASSWORD);
    }

    private int sessionCount(Member member) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class,
                String.valueOf(member.getId()));
        return count == null ? 0 : count;
    }
}
