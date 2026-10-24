package com.moewadeul.memory.api.report;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.UUID;
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
class MemoryReportApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String YESTERDAY = "2026-10-04";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-33][MM-10] 같은 회원이 같은 기록을 두 번 신고하면 처음은 201, 두 번째는 409 MEMORY_ALREADY_REPORTED이고 신고는 한 건만 남는다")
    void duplicateReportIsConflict() {
        // given
        long memoryId = writePublic(verifiedSession(saveMember()));
        Cookie reporter = verifiedSession(saveMember());

        // when
        MvcTestResult first = report(memoryId, reporter, "{\"reason\":\"SPAM\",\"content\":\"광고 사진입니다\"}");
        MvcTestResult second = report(memoryId, reporter, "{\"reason\":\"ABUSE\"}");

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("MEMORY_ALREADY_REPORTED");
        assertThat(reportRows(memoryId)).isEqualTo(1);
        assertThat(status(memoryId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-33][MM-10] 다섯 번째 신고로 검토 대기가 되면 남에게는 상세·좋아요가 404이고 공개 피드와 회원 추억지도에서 빠진다")
    void fifthReportHidesMemoryFromOthers() {
        // given
        Member author = saveMember();
        Cookie authorSession = verifiedSession(author);
        long memoryId = writePublic(authorSession);
        insertReviewPendingReports(memoryId, 4);
        Cookie viewer = verifiedSession(saveMember());

        // when
        MvcTestResult fifth = report(memoryId, viewer, "{\"reason\":\"PRIVACY\"}");

        // then
        assertThat(fifth).hasStatus(HttpStatus.CREATED);
        assertThat(status(memoryId)).isEqualTo("PENDING_REVIEW");
        assertThat(mvc.get().uri("/api/memories/" + memoryId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/memories/" + memoryId).cookie(viewer).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(send(mvc.put().uri("/api/memories/" + memoryId + "/like"), viewer))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/memories/feed").exchange())
                .bodyJson()
                .extractingPath("$.content")
                .asArray()
                .isEmpty();
        assertThat(mvc.get().uri("/api/members/" + author.getId() + "/memories").exchange())
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
    }

    @Test
    @DisplayName("[F-33][MM-10] 작성자는 검토 대기인 자기 기록을 상세와 내 기록 목록에서 상태와 함께 보고, 공개 범위를 바꿔도 상태는 그대로다")
    void authorStillSeesPendingMemoryWithStatus() {
        // given
        Cookie authorSession = verifiedSession(saveMember());
        long memoryId = writePublic(authorSession);
        insertReviewPendingReports(memoryId, 4);
        report(memoryId, verifiedSession(saveMember()), "{\"reason\":\"SPAM\"}");

        // when
        MvcTestResult detail =
                mvc.get().uri("/api/memories/" + memoryId).cookie(authorSession).exchange();
        MvcTestResult mine =
                mvc.get().uri("/api/me/memories").cookie(authorSession).exchange();
        MvcTestResult madePrivate = send(
                mvc.patch()
                        .uri("/api/memories/" + memoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"),
                authorSession);
        MvcTestResult madePublic = send(
                mvc.patch()
                        .uri("/api/memories/" + memoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"),
                authorSession);

        // then
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().extractingPath("$.status").isEqualTo("PENDING_REVIEW");
        assertThat(mine).bodyJson().extractingPath("$[0].status").isEqualTo("PENDING_REVIEW");
        assertThat(madePrivate).hasStatus(HttpStatus.OK);
        assertThat(madePublic).hasStatus(HttpStatus.OK);
        assertThat(madePublic).bodyJson().extractingPath("$.status").isEqualTo("PENDING_REVIEW");
        assertThat(status(memoryId)).isEqualTo("PENDING_REVIEW");
        assertThat(mvc.get().uri("/api/memories/" + memoryId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-33][MM-10] 정상 공개 기록의 상세는 작성자에게만 status를 주고, 다른 사람의 응답에는 status 필드가 없다")
    void statusIsOnlyForAuthor() {
        // given
        Cookie authorSession = verifiedSession(saveMember());
        long memoryId = writePublic(authorSession);

        // when
        MvcTestResult byAuthor =
                mvc.get().uri("/api/memories/" + memoryId).cookie(authorSession).exchange();
        MvcTestResult byOther = mvc.get().uri("/api/memories/" + memoryId).exchange();

        // then
        assertThat(byAuthor).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(byOther).hasStatus(HttpStatus.OK);
        assertThat(byOther).bodyJson().doesNotHavePath("$.status");
    }

    @Test
    @DisplayName("[F-33][MM-10] 비공개 기록, 숨긴 기록, 없는 기록을 신고하면 404이고 신고가 남지 않는다")
    void reportingInvisibleMemoryIsNotFound() {
        // given
        Cookie authorSession = verifiedSession(saveMember());
        long privateId = writeMemory(authorSession, "PRIVATE");
        long hiddenId = writePublic(authorSession);
        jdbc.update("UPDATE memory SET status = 'HIDDEN' WHERE id = ?", hiddenId);
        Cookie reporter = verifiedSession(saveMember());

        // when
        MvcTestResult privateReport = report(privateId, reporter, "{\"reason\":\"SPAM\"}");
        MvcTestResult hiddenReport = report(hiddenId, reporter, "{\"reason\":\"SPAM\"}");
        MvcTestResult missingReport = report(999999L, reporter, "{\"reason\":\"SPAM\"}");

        // then
        assertThat(privateReport).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(privateReport).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(hiddenReport).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(missingReport).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_report", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-33][MM-10] 모르는 사유는 400 INVALID_INPUT, 이메일 인증 전이면 403, 로그인하지 않으면 401이다")
    void rejectsInvalidReasonAndUnverifiedMember() {
        // given
        long memoryId = writePublic(verifiedSession(saveMember()));

        // when
        MvcTestResult unknownReason = report(memoryId, verifiedSession(saveMember()), "{\"reason\":\"UNKNOWN\"}");
        MvcTestResult unverified = report(memoryId, login(saveMember()), "{\"reason\":\"SPAM\"}");
        MvcTestResult anonymous = mvc.post()
                .uri("/api/memories/" + memoryId + "/reports")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}")
                .exchange();

        // then
        assertThat(unknownReason).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknownReason).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(unverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(reportRows(memoryId)).isZero();
    }

    @Test
    @DisplayName("[F-33][MM-10] 작성자가 기록을 지우면 그 기록의 신고도 함께 지워진다")
    void deletingMemoryRemovesReports() {
        // given
        Cookie authorSession = verifiedSession(saveMember());
        long memoryId = writePublic(authorSession);
        report(memoryId, verifiedSession(saveMember()), "{\"reason\":\"SPAM\"}");
        assertThat(reportRows(memoryId)).isEqualTo(1);

        // when
        MvcTestResult deleted = send(mvc.delete().uri("/api/memories/" + memoryId), authorSession);

        // then
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(reportRows(memoryId)).isZero();
    }

    private MvcTestResult report(long memoryId, Cookie session, String body) {
        return send(
                mvc.post()
                        .uri("/api/memories/" + memoryId + "/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                session);
    }

    // 신고자마다 로그인하지 않아도 되도록 회원 행만 만들고 검토 전 신고를 직접 넣는다.
    private void insertReviewPendingReports(long memoryId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update(
                    "INSERT INTO memory_report (memory_id, reporter_id, reason, content, reviewed_at, created_at)"
                            + " VALUES (?, ?, 'SPAM', NULL, NULL, ?)",
                    memoryId,
                    saveMember().getId(),
                    Timestamp.from(clock.instant()));
        }
    }

    private int reportRows(long memoryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM memory_report WHERE memory_id = ?", Integer.class, memoryId);
    }

    private String status(long memoryId) {
        return jdbc.queryForObject("SELECT status FROM memory WHERE id = ?", String.class, memoryId);
    }

    private long writePublic(Cookie session) {
        return writeMemory(session, "PUBLIC");
    }

    private long writeMemory(Cookie session, String visibility) {
        MvcTestResult created = send(
                mvc.post()
                        .uri("/api/memories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"visibility\":\"%s\"}"
                                        .formatted(YESTERDAY, visibility)),
                session);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.<Integer>read(created.getResponse().getContentAsString(), "$.memoryId");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session) {
        return builder.with(csrf())
                .cookie(session)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange();
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(VALID_PASSWORD)).build());
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
        String code = emailVerificationService
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
