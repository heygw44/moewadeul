package com.moewadeul.common.security;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemberNotVerifiedAuthorizationIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final long MEMBER_ID = 7L;
    private static final String USER_ROLE = "USER";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    static Stream<Arguments> publicReadEndpoints() {
        return AuthorizationMatrix.PUBLIC.stream()
                .filter(endpoint -> endpoint.method() == HttpMethod.GET)
                .map(endpoint -> Arguments.of(endpoint.method(), endpoint.path()));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("com.moewadeul.common.security.AuthorizationMatrix#verifiedOnlyEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원이 인증 회원 이상의 경로를 부르면 403 MEMBER_NOT_VERIFIED로 응답한다")
    void unverifiedMemberIsRejectedOnVerifiedOnlyEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, false);

        MvcTestResult result = call(method, path, session);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("이메일 인증이 필요해요.");
        String headerTraceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(headerTraceId).isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(headerTraceId);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("com.moewadeul.common.security.AuthorizationMatrix#verifiedOnlyEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마친 회원은 인증 회원 이상의 경로에서 인증·권한 오류 없이 컨트롤러까지 간다")
    void verifiedMemberPassesVerifiedOnlyEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, true);

        MvcTestResult result = call(method, path, session);

        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
        if (result.getResponse().getStatus() == 403) {
            assertThat(result).bodyJson().extractingPath("$.code").isNotIn("MEMBER_NOT_VERIFIED", "ACCESS_DENIED");
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("com.moewadeul.common.security.AuthorizationMatrix#verifiedOnlyEndpoints")
    @DisplayName("[F-01][TR-03] 로그인하지 않고 인증 회원 이상의 경로를 부르면 403이 아니라 401 AUTHENTICATION_REQUIRED로 응답한다")
    void anonymousIsUnauthorizedOnVerifiedOnlyEndpoints(HttpMethod method, String path) {
        MvcTestResult result = call(method, path, null);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("com.moewadeul.common.security.AuthorizationMatrix#loginLevelEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원도 로그인만 필요한 경로는 인증·권한 오류 없이 쓸 수 있다")
    void unverifiedMemberPassesLoginLevelEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, false);

        MvcTestResult result = call(method, path, session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("com.moewadeul.common.security.AuthorizationMatrix#allLoginLevelEndpoints")
    @DisplayName("[F-01][NFR-07] 로그인하지 않고 로그인이 필요한 경로를 부르면 401 AUTHENTICATION_REQUIRED로 응답한다")
    void anonymousIsUnauthorizedOnLoginLevelEndpoints(HttpMethod method, String path) {
        MvcTestResult result = call(method, path, null);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("publicReadEndpoints")
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원도 공개 읽기 경로는 쓸 수 있다")
    void unverifiedMemberPassesPublicReadEndpoints(HttpMethod method, String path) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, false);

        MvcTestResult result = call(method, path, session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("[F-01][TR-03] 이메일 인증을 마치지 않은 회원도 인증 코드 확인과 재발송 API에는 닿는다")
    void unverifiedMemberReachesEmailVerificationApis() {
        Cookie session = loginAsRealMember();

        MvcTestResult verify = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}")
                .exchange();
        MvcTestResult resend = mvc.post()
                .uri("/api/me/email-verification/resend")
                .with(csrf())
                .cookie(session)
                .exchange();

        assertThat(verify).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(verify).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_CODE_INVALID");
        assertThat(resend).hasStatus(HttpStatus.NO_CONTENT);
    }

    @ParameterizedTest(name = "verified={0}")
    @MethodSource("verifiedFlags")
    @DisplayName("[F-01][ADR-002] CSRF 토큰이 없는 쓰기 요청은 인증 여부와 상관없이 403 ACCESS_DENIED로 응답한다")
    void missingCsrfTokenIsAccessDeniedRegardlessOfVerification(boolean emailVerified) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, emailVerified);

        MvcTestResult result = mvc.post()
                .uri("/api/bakjis")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    static Stream<Arguments> verifiedFlags() {
        return Stream.of(Arguments.of(false), Arguments.of(true));
    }

    @ParameterizedTest(name = "verified={0}")
    @MethodSource("verifiedFlags")
    @DisplayName("[F-01][ADR-002] 일반 회원이 관리자 경로를 부르면 인증 여부와 상관없이 403 ACCESS_DENIED로 응답한다")
    void adminPathIsAccessDeniedForUserRegardlessOfVerification(boolean emailVerified) {
        Cookie session = loginAsProbe(MEMBER_ID, USER_ROLE, emailVerified);

        MvcTestResult result = call(HttpMethod.GET, "/api/admin/audit-logs", session);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("[F-01][ADR-002] 관리자 경로는 관리자 역할만 보고 이메일 인증 권한은 요구하지 않는다")
    void adminRoleDoesNotNeedEmailVerifiedAuthority() {
        Cookie session = loginAsProbe(1L, "ADMIN", false);

        MvcTestResult result = call(HttpMethod.GET, "/api/admin/audit-logs", session);

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    private MvcTestResult call(HttpMethod method, String path, Cookie session) {
        MockMvcTester.MockMvcRequestBuilder request =
                mvc.method(method).uri(path).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (session != null) {
            request.cookie(session);
        }
        return request.content("{}").exchange();
    }

    private Cookie loginAsProbe(long memberId, String role, boolean emailVerified) {
        MvcTestResult result = mvc.post()
                .uri("/security-test/login")
                .param("memberId", String.valueOf(memberId))
                .param("role", role)
                .param("emailVerified", String.valueOf(emailVerified))
                .exchange();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie loginAsRealMember() {
        Member member = memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .build());
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
