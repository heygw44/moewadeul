package com.moewadeul.program.api.enrollment;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 행사 신청의 결제와 취소를 신청자 본인만 할 수 있는지 HTTP 요청으로 확인한다.
 *
 * <p>경로에는 신청 ID만 있어서, 다른 회원이 남의 신청 ID를 넣어 보낼 수 있다. 서비스는 신청자와 로그인한 회원을 비교해 거부해야 한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProgramApplicationOwnerApiIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmailVerificationService emailVerificationService;

    private ProgramApplyFixture fixture;
    private long applicationId;
    private Cookie otherSession;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
        Member applicant = saveMember();
        long programId = fixture.saveProgram(3, false);
        applicationId = fixture.saveApplication(programId, applicant.getId(), "PENDING_PAYMENT");
        otherSession = login(saveMember());
    }

    @Test
    @DisplayName("[F-19][NFR-07] 신청자가 아닌 회원이 남의 신청을 결제하면 403 ACCESS_DENIED로 응답하고 신청은 결제 대기로 남는다")
    void otherMemberCannotPay() {
        // when
        MvcTestResult result = mvc.post()
                .uri("/api/program-applications/" + applicationId + "/pay")
                .header("Idempotency-Key", "other-pay-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"FAKE_CARD\"}")
                .with(csrf())
                .cookie(otherSession)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("[F-19][NFR-07] 신청자가 아닌 회원이 남의 신청을 취소하면 403 ACCESS_DENIED로 응답하고 신청은 결제 대기로 남는다")
    void otherMemberCannotCancel() {
        // when
        MvcTestResult result = mvc.post()
                .uri("/api/program-applications/" + applicationId + "/cancel")
                .with(csrf())
                .cookie(otherSession)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("PENDING_PAYMENT");
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
    }

    // 쓰기 요청은 이메일 인증을 마친 회원만 보낼 수 있어서, 로그인한 뒤 인증 코드를 발급받아 인증한다.
    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + member.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                .with(csrf())
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        String code = emailVerificationService
                .issueFor(member.getId(), "127.0.0.1")
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
