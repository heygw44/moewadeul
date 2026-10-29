package com.moewadeul.community.api.post;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static com.moewadeul.community.api.CommunityApiFixture.postIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.community.api.CommunityApiFixture;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
class CommunityPostWriteApiIntegrationTest {

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

    private CommunityApiFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationIssuer, jdbc);
    }

    @Test
    @DisplayName("[F-29][CM-02] 인증 회원이 글을 쓰면 201이고, 비로그인 사용자의 목록 맨 앞과 상세에 그 글이 보인다")
    void writeThenListAndDetailAsAnonymous() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Member author = fixture.saveMember("새벽능선");
        Cookie session = fixture.verifiedSession(author);
        fixture.writePost(session, "먼저 쓴 글", "첫 본문", null);

        // when
        MvcTestResult created = fixture.send(
                mvc.post().uri(POSTS), session, "{\"title\":\"텐트 후기\",\"content\":\"가볍다\",\"spotId\":" + spotId + "}");
        long postId = postIdOf(created);
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(list).bodyJson().extractingPath("$.content[0].postId").isEqualTo((int) postId);
        assertThat(list).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(list).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(list).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().isStrictlyEqualTo("""
                {
                  "postId": %d,
                  "title": "텐트 후기",
                  "content": "가볍다",
                  "images": [],
                  "author": { "memberId": %d, "nickname": "새벽능선" },
                  "spot": { "spotId": %d, "name": "능선 끝 평지" },
                  "likeCount": 0,
                  "commentCount": 0,
                  "viewCount": 1,
                  "createdAt": "2026-10-05T03:00:00Z",
                  "updatedAt": "2026-10-05T03:00:00Z"
                }
                """.formatted(postId, author.getId(), spotId));
    }

    @Test
    @DisplayName("[F-29][NFR-03] 같은 Idempotency-Key로 글을 두 번 쓰면 글은 하나만 만들고, 두 번째에도 처음 postId를 응답한다")
    void sameKeyWritesOnePost() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        String key = UUID.randomUUID().toString();
        String body = "{\"title\":\"텐트 후기\",\"content\":\"가볍다\"}";

        // when
        MvcTestResult first = fixture.postWithKey(POSTS, session, key, body);
        MvcTestResult second = fixture.postWithKey(POSTS, session, key, body);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(postIdOf(second)).isEqualTo(postIdOf(first));
        assertThat(fixture.postCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-02] 숨겨졌거나 없는 장소를 연결해 글을 쓰면 400 INVALID_INPUT이고 글은 저장되지 않는다")
    void writeWithHiddenOrMissingSpotIsRejected() {
        // given
        long hidden = fixture.insertSpot("숨김 장소", "HIDDEN");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        String template = "{\"title\":\"제목\",\"content\":\"본문\",\"spotId\":%d}";

        // when
        MvcTestResult linkHidden = fixture.send(mvc.post().uri(POSTS), session, template.formatted(hidden));
        MvcTestResult linkMissing = fixture.send(mvc.post().uri(POSTS), session, template.formatted(999_999L));

        // then
        assertThat(linkHidden).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(linkHidden).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(linkMissing).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(linkMissing).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(fixture.postCount()).isZero();
    }

    @Test
    @DisplayName("[F-29][TR-03] 비로그인 쓰기는 401, 이메일 인증 전 회원의 쓰기는 403 MEMBER_NOT_VERIFIED이고 글은 저장되지 않는다")
    void writeRequiresVerifiedMember() {
        // given
        String body = "{\"title\":\"제목\",\"content\":\"본문\"}";
        Member unverified = fixture.saveMember(null);
        Cookie unverifiedSession = fixture.loginOnly(unverified);

        // when
        MvcTestResult anonymous = mvc.post()
                .uri(POSTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        MvcTestResult notVerified = fixture.send(mvc.post().uri(POSTS), unverifiedSession, body);

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(notVerified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(notVerified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(fixture.postCount()).isZero();
    }
}
