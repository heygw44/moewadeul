package com.moewadeul.member.api.password;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class PasswordChangeApiIntegrationTest {

    private static final String OLD_PASSWORD = "Valid-pass1";
    private static final String NEW_PASSWORD = "Newer-pass2";
    private static final String WRONG_PASSWORD = "Wrong-pass3";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String CHANGE_PATH = "/api/me/password";
    private static final String LOGIN_REQUIRED_PATH = "/api/me";

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

    @Test
    @DisplayName("[F-32][PW-07] 이메일 인증 전인 회원이 바꾸면 204이고, 예전 비밀번호는 401 LOGIN_FAILED, 새 비밀번호는 200이다")
    void changesPassword() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult result = change(session, OLD_PASSWORD, NEW_PASSWORD);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult oldLogin = loginRequest(member.getEmail(), OLD_PASSWORD);
        assertThat(oldLogin).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(oldLogin).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
        assertThat(loginRequest(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-32][PW-07] 바꾸면 같은 회원의 다른 세션은 401이 되고, 요청한 세션은 새 ID로 유지되며 다른 회원의 세션은 그대로다")
    void keepsOnlyTheRequestingSessionWithNewId() {
        Member member = saveMember();
        Member other = saveMember();
        Cookie current = login(member);
        Cookie another = login(member);
        Cookie otherSession = login(other);
        assertThat(sessionCount(member)).isEqualTo(2);

        MvcTestResult result = change(current, OLD_PASSWORD, NEW_PASSWORD);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        Cookie renewed = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(renewed).isNotNull();
        assertThat(renewed.getValue()).isNotEqualTo(current.getValue());
        assertThat(sessionCount(member)).isEqualTo(1);
        assertThat(getWithSession(renewed)).hasStatus(HttpStatus.OK);
        assertThat(getWithSession(current)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(getWithSession(another)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionCount(other)).isEqualTo(1);
        assertThat(getWithSession(otherSession)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-32][PW-07] 현재 비밀번호가 틀리면 400 MEMBER_CURRENT_PASSWORD_MISMATCH이고, 실패 기록이 남고 비밀번호와 세션은 그대로다")
    void wrongCurrentPasswordIsRecordedAsFailure() {
        Member member = saveMember();
        Cookie current = login(member);
        Cookie another = login(member);

        MvcTestResult result = change(current, WRONG_PASSWORD, NEW_PASSWORD);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_CURRENT_PASSWORD_MISMATCH");
        assertThat(failureCount(member)).isEqualTo(1);
        assertThat(sessionCount(member)).isEqualTo(2);
        assertThat(getWithSession(another)).hasStatus(HttpStatus.OK);
        assertThat(loginRequest(member.getEmail(), OLD_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName(
            "[F-32][PW-01][PW-07] 새 비밀번호가 규칙을 어기면 400 MEMBER_PASSWORD_POLICY, 현재와 같으면 400 MEMBER_PASSWORD_UNCHANGED이고 실패로 세지 않는다")
    void rejectsInvalidNewPasswordWithoutCountingFailure() {
        Member member = saveMember();
        Cookie session = login(member);

        MvcTestResult weak = change(session, OLD_PASSWORD, "short1!");
        MvcTestResult same = change(session, OLD_PASSWORD, OLD_PASSWORD);

        assertThat(weak).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(weak).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_POLICY");
        assertThat(same).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(same).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_UNCHANGED");
        assertThat(failureCount(member)).isZero();
        assertThat(getWithSession(session)).hasStatus(HttpStatus.OK);
        assertThat(loginRequest(member.getEmail(), OLD_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-32][PW-03][PW-07] 현재 비밀번호를 5번 틀리면 변경과 로그인 모두 429 LOGIN_LOCKED와 Retry-After를 받는다")
    void repeatedMismatchLocksChangeAndLogin() {
        Member member = saveMember();
        Cookie session = login(member);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(change(session, WRONG_PASSWORD, NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        MvcTestResult lockedChange = change(session, OLD_PASSWORD, NEW_PASSWORD);
        MvcTestResult lockedLogin = loginRequest(member.getEmail(), OLD_PASSWORD);

        assertThat(lockedChange).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(lockedChange).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_LOCKED");
        assertThat(lockedChange.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .isNotBlank();
        assertThat(lockedLogin).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(failureCount(member)).isEqualTo(5);
    }

    @Test
    @DisplayName("[F-32][PW-06][PW-07] 바꾸기 전의 로그인 실패는 잠금 계산에서 빠진다")
    void failuresBeforeChangeAreNotCounted() {
        Member member = saveMember();
        Cookie session = login(member);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThat(loginRequest(member.getEmail(), WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        }
        clock.advance(Duration.ofSeconds(1));
        assertThat(change(session, OLD_PASSWORD, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
        clock.advance(Duration.ofSeconds(1));

        assertThat(loginRequest(member.getEmail(), WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(loginRequest(member.getEmail(), NEW_PASSWORD)).hasStatus(HttpStatus.OK);
    }

    // 회원을 만든 시각과 실패 시각이 같으면 잠금 계산이 그 실패를 비밀번호를 정하기 전의 것으로 보고 빼므로, 시계를 1초 옮겨 둔다.
    private Member saveMember() {
        Member member = memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .now(clock.instant())
                .build());
        clock.advance(Duration.ofSeconds(1));
        return member;
    }

    private Cookie login(Member member) {
        MvcTestResult result = loginRequest(member.getEmail(), OLD_PASSWORD);
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private MvcTestResult loginRequest(String email, String password) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult change(Cookie session, String currentPassword, String newPassword) {
        String body = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                .formatted(currentPassword, newPassword, newPassword);
        return mvc.put()
                .uri(CHANGE_PATH)
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult getWithSession(Cookie session) {
        return mvc.get().uri(LOGIN_REQUIRED_PATH).cookie(session).exchange();
    }

    private int sessionCount(Member member) {
        return count("SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(member.getId()));
    }

    private int failureCount(Member member) {
        return count("login_history WHERE member_id = ? AND success = false", member.getId());
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
