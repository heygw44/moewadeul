package com.moewadeul.community.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class CommunityApiFixture {

    public static final String POSTS = "/api/community/posts";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

    private final MockMvcTester mvc;
    private final MemberJpaRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationIssuer emailVerificationIssuer;
    private final JdbcTemplate jdbc;

    public CommunityApiFixture(
            MockMvcTester mvc,
            MemberJpaRepository memberRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationIssuer emailVerificationIssuer,
            JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationIssuer = emailVerificationIssuer;
        this.jdbc = jdbc;
    }

    public Member saveMember(String nickname) {
        return memberRepository.saveAndFlush(aMember()
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .nickname(nickname)
                .build());
    }

    public Cookie verifiedSession(Member member) {
        MvcTestResult login = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(login).hasStatus(HttpStatus.OK);
        Cookie session = login.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
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

    public long insertSpot(String name, String status) {
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, ?, NOW(6), NOW(6))",
                name,
                status);
        return jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    public void setSpotStatus(long spotId, String status) {
        jdbc.update("UPDATE spot SET status = ? WHERE id = ?", status, spotId);
    }

    public MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session, String body) {
        MockMvcTester.MockMvcRequestBuilder request = builder.with(csrf())
                .cookie(session)
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    public MvcTestResult postWithKey(String uri, Cookie session, String key, String body) {
        return mvc.post()
                .uri(uri)
                .with(csrf())
                .cookie(session)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    public long writePost(Cookie session, String title, String content, Long spotId) {
        String spotPart = spotId == null ? "" : ",\"spotId\":" + spotId;
        MvcTestResult created = send(
                mvc.post().uri(POSTS),
                session,
                "{\"title\":\"%s\",\"content\":\"%s\"%s}".formatted(title, content, spotPart));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return postIdOf(created);
    }

    public static long postIdOf(MvcTestResult created) {
        AtomicInteger postId = new AtomicInteger();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.postId")
                .asNumber()
                .satisfies(number -> postId.set(number.intValue()));
        return postId.get();
    }

    public String postStatus(long postId) {
        return jdbc.queryForObject("SELECT status FROM community_post WHERE id = ?", String.class, postId);
    }

    public int postCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_post", Integer.class);
    }

    public long writeComment(Cookie session, long postId, String content, Long parentId) {
        MvcTestResult created = writeCommentRaw(session, postId, content, parentId);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        AtomicInteger commentId = new AtomicInteger();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.commentId")
                .asNumber()
                .satisfies(number -> commentId.set(number.intValue()));
        return commentId.get();
    }

    public MvcTestResult writeCommentRaw(Cookie session, long postId, String content, Long parentId) {
        String parentPart = parentId == null ? "" : ",\"parentId\":" + parentId;
        return send(
                mvc.post().uri(POSTS + "/" + postId + "/comments"),
                session,
                "{\"content\":\"%s\"%s}".formatted(content, parentPart));
    }

    public void setCommentStatus(long commentId, String status) {
        jdbc.update("UPDATE community_comment SET status = ? WHERE id = ?", status, commentId);
    }

    public String commentStatus(long commentId) {
        return jdbc.queryForObject("SELECT status FROM community_comment WHERE id = ?", String.class, commentId);
    }

    public String commentContent(long commentId) {
        return jdbc.queryForObject("SELECT content FROM community_comment WHERE id = ?", String.class, commentId);
    }

    public int commentCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_comment", Integer.class);
    }

    public Cookie loginOnly(Member member) {
        MvcTestResult login = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(login).hasStatus(HttpStatus.OK);
        return login.getResponse().getCookie(SESSION_COOKIE);
    }
}
