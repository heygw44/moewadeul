package com.moewadeul.member.api.login;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.application.PasswordMatchPause;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.password.PasswordResetToken;
import com.moewadeul.member.domain.password.PasswordResetTokenRepository;
import com.moewadeul.member.domain.password.ResetToken;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
@Import(PasswordMatchPause.Config.class)
class LoginSessionRaceIntegrationTest {

    private static final String OLD_PASSWORD = "Valid-pass1";
    private static final String NEW_PASSWORD = "Newer-pass2";
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

    @AfterEach
    void clearPause() {
        PasswordMatchPause.clear();
    }

    @Test
    @DisplayName("[PW-02] 비밀번호를 비교하는 사이에 재설정이 끝나면, 그 로그인 요청은 401이고 세션을 남기지 않는다")
    void loginOverlappingPasswordResetLeavesNoSession() throws Exception {
        Member member = memberFixture.saveWithPassword(OLD_PASSWORD);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PasswordMatchPause.arm(OLD_PASSWORD);
            Future<MvcTestResult> login = executor.submit(() -> loginRequest(member.getEmail(), OLD_PASSWORD));
            PasswordMatchPause.awaitPaused();

            MvcTestResult confirm = postJson("/api/auth/password-reset/confirm", confirmBody(issueToken(member)));
            assertThat(confirm).hasStatus(HttpStatus.NO_CONTENT);
            PasswordMatchPause.release();

            assertThat(login.get(WAIT_SECONDS, TimeUnit.SECONDS)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(sessionCount(member)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[SN-12] 비밀번호를 비교하는 사이에 이용 정지가 확정되면, 그 로그인 요청은 403이고 세션을 남기지 않는다")
    void loginOverlappingSuspensionLeavesNoSession() throws Exception {
        Member member = memberFixture.saveWithPassword(OLD_PASSWORD);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PasswordMatchPause.arm(OLD_PASSWORD);
            Future<MvcTestResult> login = executor.submit(() -> loginRequest(member.getEmail(), OLD_PASSWORD));
            PasswordMatchPause.awaitPaused();

            jdbc.update("UPDATE member SET status = 'SUSPENDED', suspended_until = NULL WHERE id = ?", member.getId());
            PasswordMatchPause.release();

            assertThat(login.get(WAIT_SECONDS, TimeUnit.SECONDS)).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(sessionCount(member)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private String issueToken(Member member) {
        ResetToken token = ResetToken.generate(new SecureRandom());
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(token.value()), clock.instant()));
        return token.value();
    }

    private MvcTestResult loginRequest(String email, String password) {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
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
