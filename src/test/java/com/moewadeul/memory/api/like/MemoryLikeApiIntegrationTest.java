package com.moewadeul.memory.api.like;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class MemoryLikeApiIntegrationTest {

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

    @Test
    @DisplayName("[F-33][MM-09] 공개 기록에 좋아요를 두 번 눌러도 liked true에 1개이고, 취소를 두 번 해도 liked false에 0개다")
    void likeAndUnlikeAreRepeatable() {
        // given
        Cookie owner = verifiedSession(saveMember());
        Cookie viewer = verifiedSession(saveMember());
        long memoryId = writePublic(owner);

        // when
        MvcTestResult like1 = send(mvc.put().uri(likeUri(memoryId)), viewer);
        MvcTestResult like2 = send(mvc.put().uri(likeUri(memoryId)), viewer);
        MvcTestResult unlike1 = send(mvc.delete().uri(likeUri(memoryId)), viewer);
        MvcTestResult unlike2 = send(mvc.delete().uri(likeUri(memoryId)), viewer);

        // then
        for (MvcTestResult liked : new MvcTestResult[] {like1, like2}) {
            assertThat(liked).hasStatus(HttpStatus.OK);
            assertThat(liked).bodyJson().extractingPath("$.liked").isEqualTo(true);
            assertThat(liked).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
        }
        for (MvcTestResult unliked : new MvcTestResult[] {unlike1, unlike2}) {
            assertThat(unliked).hasStatus(HttpStatus.OK);
            assertThat(unliked).bodyJson().extractingPath("$.liked").isEqualTo(false);
            assertThat(unliked).bodyJson().extractingPath("$.likeCount").isEqualTo(0);
        }
        assertThat(likeRows(memoryId)).isZero();
    }

    @Test
    @DisplayName("[F-33][MM-09] 여러 회원의 좋아요는 각자 한 번씩 세고, 작성자도 자기 공개 기록에 누를 수 있다")
    void countsEachMemberOnceIncludingAuthor() {
        // given
        Cookie owner = verifiedSession(saveMember());
        long memoryId = writePublic(owner);

        // when
        send(mvc.put().uri(likeUri(memoryId)), verifiedSession(saveMember()));
        send(mvc.put().uri(likeUri(memoryId)), verifiedSession(saveMember()));
        MvcTestResult byAuthor = send(mvc.put().uri(likeUri(memoryId)), owner);

        // then
        assertThat(byAuthor).hasStatus(HttpStatus.OK);
        assertThat(byAuthor).bodyJson().extractingPath("$.likeCount").isEqualTo(3);
        assertThat(likeRows(memoryId)).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-33][TR-03] 이메일 인증 전 회원은 좋아요가 403 MEMBER_NOT_VERIFIED이고, 로그인하지 않으면 401이다")
    void requiresVerifiedMember() {
        // given
        Cookie owner = verifiedSession(saveMember());
        long memoryId = writePublic(owner);
        Cookie unverified = login(saveMember());

        // when
        MvcTestResult unverifiedLike = send(mvc.put().uri(likeUri(memoryId)), unverified);
        MvcTestResult unverifiedUnlike = send(mvc.delete().uri(likeUri(memoryId)), unverified);
        MvcTestResult anonymous = mvc.put().uri(likeUri(memoryId)).with(csrf()).exchange();

        // then
        assertThat(unverifiedLike).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedLike).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(unverifiedUnlike).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(likeRows(memoryId)).isZero();
    }

    @Test
    @DisplayName("[F-33][MM-09] 비공개 기록과 없는 기록의 좋아요는 작성자에게도 404이고, 다시 공개하면 남아 있던 좋아요 수가 돌아온다")
    void privateAndMissingMemoryAreNotFound() {
        // given
        Cookie owner = verifiedSession(saveMember());
        Cookie viewer = verifiedSession(saveMember());
        long memoryId = writePublic(owner);
        send(mvc.put().uri(likeUri(memoryId)), viewer);
        send(
                mvc.patch()
                        .uri("/api/memories/" + memoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"),
                owner);

        // when
        MvcTestResult byViewer = send(mvc.put().uri(likeUri(memoryId)), viewer);
        MvcTestResult byAuthor = send(mvc.put().uri(likeUri(memoryId)), owner);
        MvcTestResult unlikeByViewer = send(mvc.delete().uri(likeUri(memoryId)), viewer);
        MvcTestResult missing = send(mvc.put().uri(likeUri(999999)), viewer);
        send(
                mvc.patch()
                        .uri("/api/memories/" + memoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"),
                owner);
        MvcTestResult reopened = mvc.get().uri("/api/memories/" + memoryId).exchange();

        // then
        assertThat(byViewer).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(byAuthor).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unlikeByViewer).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(byViewer).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(likeRows(memoryId)).isEqualTo(1);
        assertThat(reopened).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
    }

    @Test
    @DisplayName("[F-33][MM-09] 기록을 지우면 그 기록의 좋아요 행도 함께 지워진다")
    void deletingMemoryRemovesLikes() {
        // given
        Cookie owner = verifiedSession(saveMember());
        long memoryId = writePublic(owner);
        send(mvc.put().uri(likeUri(memoryId)), verifiedSession(saveMember()));
        send(mvc.put().uri(likeUri(memoryId)), verifiedSession(saveMember()));
        assertThat(likeRows(memoryId)).isEqualTo(2);

        // when
        MvcTestResult deleted = send(mvc.delete().uri("/api/memories/" + memoryId), owner);

        // then
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(likeRows(memoryId)).isZero();
    }

    private static String likeUri(long memoryId) {
        return "/api/memories/" + memoryId + "/like";
    }

    private int likeRows(long memoryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM memory_like WHERE memory_id = ?", Integer.class, memoryId);
    }

    private long writePublic(Cookie session) {
        MvcTestResult created = send(
                mvc.post()
                        .uri("/api/memories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"visibility\":\"PUBLIC\"}"
                                        .formatted(YESTERDAY)),
                session);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.<Integer>read(created.getResponse().getContentAsString(), "$.memoryId");
        } catch (java.io.UnsupportedEncodingException e) {
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
                .issueFor(member.getId(), LOCAL_IP)
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
