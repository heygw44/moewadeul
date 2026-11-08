package com.moewadeul.member.api.signup;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.signup.DisposableEmailDomain;
import com.moewadeul.member.domain.signup.DisposableEmailDomainSource;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.member.infra.signup.DisposableEmailDomainJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
class DisposableEmailDomainAdminApiIntegrationTest {

    private static final String DOMAINS = "/api/admin/disposable-email-domains";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SIGNUP_PASSWORD = "Valid-pass-1!";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private DisposableEmailDomainJpaRepository publicDomainRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;
    private long adminId;
    private Cookie adminSession;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
    }

    @Test
    @DisplayName("[F-01][EV-04] 관리자가 추가한 도메인과 그 하위 도메인으로는 가입할 수 없고, 삭제하면 다시 가입할 수 있다")
    void addedDomainBlocksSignupUntilDeleted() {
        // given
        MvcTestResult added = add("Throwaway-Mail.example");
        assertThat(added).hasStatus(HttpStatus.CREATED);
        assertThat(added).bodyJson().extractingPath("$.domain").isEqualTo("throwaway-mail.example");
        long id = ((Number) json(added, "$.id")).longValue();

        // when
        MvcTestResult blocked = signUp("a" + TestSequence.next() + "@throwaway-mail.example");
        MvcTestResult blockedSub = signUp("b" + TestSequence.next() + "@mx.throwaway-mail.example");
        MvcTestResult deleted = mvc.delete()
                .uri(DOMAINS + "/" + id)
                .cookie(adminSession)
                .with(csrf())
                .exchange();
        MvcTestResult allowed = signUp("c" + TestSequence.next() + "@throwaway-mail.example");

        // then
        assertThat(blocked).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(blocked).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_EMAIL");
        assertThat(blockedSub).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_EMAIL");
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(allowed).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-21][EV-04] 추가와 삭제는 같은 대상으로 감사 기록에 도메인과 함께 남는다")
    void addAndDeleteAreAudited() {
        // given
        long id = ((Number) json(add("audit-target.example"), "$.id")).longValue();

        // when
        mvc.delete().uri(DOMAINS + "/" + id).cookie(adminSession).with(csrf()).exchange();

        // then
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT admin_id, action, target_type, target_id, JSON_UNQUOTE(JSON_EXTRACT(detail, '$.domain')) AS domain"
                        + " FROM admin_audit_log ORDER BY id");
        assertThat(rows)
                .extracting(row -> row.get("action"))
                .containsExactly("DISPOSABLE_DOMAIN_ADD", "DISPOSABLE_DOMAIN_DELETE");
        assertThat(rows).allSatisfy(row -> {
            assertThat(((Number) row.get("admin_id")).longValue()).isEqualTo(adminId);
            assertThat(row.get("target_type")).isEqualTo("DISPOSABLE_EMAIL_DOMAIN");
            assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(id);
            assertThat(row.get("domain")).isEqualTo("audit-target.example");
        });
    }

    @Test
    @DisplayName("[EV-04] 목록은 관리자가 추가한 도메인만 최근에 추가한 순서로 보여 준다")
    void listShowsAdminDomainsNewestFirst() {
        // given
        publicDomainRepository.save(DisposableEmailDomain.of(
                "public-only.example", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));
        add("first.example");
        add("second.example");

        // when
        MvcTestResult result = mvc.get().uri(DOMAINS).cookie(adminSession).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        List<String> domains = json(result, "$[*].domain");
        assertThat(domains).containsExactly("second.example", "first.example");
    }

    @Test
    @DisplayName("[EV-04] 공개 목록이나 관리자 목록에 이미 있는 도메인은 409로 거절한다")
    void alreadyBlockedDomainIsConflict() {
        // given
        publicDomainRepository.save(DisposableEmailDomain.of(
                "mailinator.com", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));
        add("dup.example");

        // when
        MvcTestResult publicDup = add("MAILINATOR.com");
        MvcTestResult adminDup = add("Dup.Example");

        // then
        assertThat(publicDup).hasStatus(HttpStatus.CONFLICT);
        assertThat(publicDup).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_DOMAIN_ALREADY_BLOCKED");
        assertThat(adminDup).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_DOMAIN_ALREADY_BLOCKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_disposable_email_domain", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[EV-04] 도메인 형식이 아니면 400, 없는 id를 삭제하면 404다")
    void invalidDomainAndUnknownId() {
        // when
        MvcTestResult withAt = add("user@example.com");
        MvcTestResult noDot = add("localhost");
        MvcTestResult missing = mvc.delete()
                .uri(DOMAINS + "/999999")
                .cookie(adminSession)
                .with(csrf())
                .exchange();

        // then
        assertThat(withAt).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(noDot).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-21] 관리자가 아닌 회원은 차단 도메인을 추가할 수 없다")
    void nonAdminIsForbidden() {
        // given
        Cookie member = login(fixture.saveVerifiedMember(TestSequence.nickname()));

        // when
        MvcTestResult result = mvc.post()
                .uri(DOMAINS)
                .cookie(member)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"member.example\"}")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult add(String domain) {
        return mvc.post()
                .uri(DOMAINS)
                .cookie(adminSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"%s\"}".formatted(domain))
                .exchange();
    }

    private MvcTestResult signUp(String email) {
        String body =
                "{\"email\":\"%s\",\"password\":\"%s\",\"passwordConfirm\":\"%s\",\"nickname\":\"%s\",\"ageConfirmed\":true}"
                        .formatted(email, SIGNUP_PASSWORD, SIGNUP_PASSWORD, TestSequence.nickname());
        return mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private Cookie login(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }
}
