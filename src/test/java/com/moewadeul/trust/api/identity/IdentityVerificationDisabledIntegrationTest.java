package com.moewadeul.trust.api.identity;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.domain.identity.IdentityProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "moewadeul.identity.fake-provider.enabled=false")
class IdentityVerificationDisabledIntegrationTest {

    private static final String PATH = "/api/me/identity-verification";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationIssuer emailVerificationIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectProvider<IdentityProvider> identityProvider;

    @Test
    @DisplayName("[F-11] 가짜 공급자를 끄면 앱은 뜨고 본인확인 요청은 503 IDENTITY_UNAVAILABLE이며 기록이 남지 않는다")
    void disabledProviderAnswersServiceUnavailable() {
        Cookie session = verifiedSession(saveMember());

        MvcTestResult result = mvc.post()
                .uri(PATH)
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":2007,\"gender\":\"FEMALE\",\"demoIdentityKey\":\"demo-person-1\"}")
                .exchange();

        assertThat(identityProvider.getIfAvailable()).isNull();
        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDENTITY_UNAVAILABLE");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("지금은 본인확인을 할 수 없어요.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_verification", Long.class))
                .isZero();
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .build());
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie verifiedSession(Member member) {
        Cookie session = login(member);
        String code = emailVerificationIssuer
                .issueFor(member.getId(), LOCAL_IP, TestSequence.next())
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        return session;
    }
}
