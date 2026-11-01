package com.moewadeul.trust.api.companion;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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

@IntegrationTest
@AutoConfigureMockMvc
class CompanionReviewApiIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-03T03:00:00Z");

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

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-15][TR-01][NFR-07] 본인확인을 하지 않은 단계 0 회원이 작성할 후기 목록을 부르면 403 TRUST_LEVEL_INSUFFICIENT로 응답한다")
    void levelZeroMemberCannotReadPendingReviews() {
        // given
        Cookie session = verifiedSession(fixture.saveMember("단계영"));

        // when
        MvcTestResult result = mvc.get()
                .uri("/api/me/companion-reviews/pending")
                .cookie(session)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TRUST_LEVEL_INSUFFICIENT");
    }

    @Test
    @DisplayName("[F-15][TR-01][NFR-07] 완료된 베이스캠프의 멤버라도 단계 0이면 후기를 쓸 수 없고 403 TRUST_LEVEL_INSUFFICIENT로 응답한다")
    void levelZeroMemberCannotWriteReview() {
        // given
        long basecampId = fixture.saveBasecamp("COMPLETED", COMPLETED_AT);
        long levelZero = fixture.saveMember("단계영");
        fixture.insertMember(basecampId, levelZero, "MEMBER", "ACTIVE");
        long leaderId = fixture.leaderIdOf(basecampId);
        Cookie session = verifiedSession(levelZero);

        // when
        MvcTestResult result = mvc.post()
                .uri("/api/basecamps/" + basecampId + "/companion-reviews")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"revieweeId\":%d,\"rejoinWanted\":true}".formatted(leaderId))
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TRUST_LEVEL_INSUFFICIENT");
        assertThat(fixture.reviewCount()).isZero();
    }

    private Cookie verifiedSession(long memberId) {
        Cookie session = login(memberId);
        String code = emailVerificationIssuer
                .issueFor(memberId, LOCAL_IP, TestSequence.next())
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

    private Cookie login(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
