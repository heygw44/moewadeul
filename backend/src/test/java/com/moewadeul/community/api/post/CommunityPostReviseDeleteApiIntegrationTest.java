package com.moewadeul.community.api.post;

import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.community.api.CommunityApiFixture;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class CommunityPostReviseDeleteApiIntegrationTest {

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
    private MutableClock clock;

    private CommunityApiFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationIssuer, jdbc);
    }

    @Test
    @DisplayName("[F-29][CM-03] 작성자가 글을 고치면 보낸 필드만 바뀌고 수정 시각이 갱신된다")
    void authorRevisesOnlyGivenFields() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        long postId = fixture.writePost(session, "제목", "본문", spotId);
        clock.advance(Duration.ofHours(1));

        // when
        MvcTestResult result = fixture.send(mvc.patch().uri(POSTS + "/" + postId), session, "{\"title\":\"새 제목\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("새 제목");
        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("본문");
        assertThat(result).bodyJson().extractingPath("$.spot.spotId").isEqualTo((int) spotId);
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-10-05T03:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-10-05T04:00:00Z");
    }

    @Test
    @DisplayName("[F-29][CM-02] spotId를 null로 보내면 장소 연결이 끊기고, ACTIVE가 아닌 장소를 보내면 400이다")
    void patchDisconnectsOrRelinksSpot() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        long hidden = fixture.insertSpot("숨김 장소", "HIDDEN");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", spotId);

        // when
        MvcTestResult relinkHidden =
                fixture.send(mvc.patch().uri(POSTS + "/" + postId), session, "{\"spotId\":" + hidden + "}");
        MvcTestResult disconnect = fixture.send(mvc.patch().uri(POSTS + "/" + postId), session, "{\"spotId\":null}");

        // then
        assertThat(relinkHidden).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(relinkHidden).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(disconnect).hasStatus(HttpStatus.OK);
        assertThat(disconnect).bodyJson().doesNotHavePath("$.spot");
        assertThat(mvc.get().uri(POSTS + "/" + postId).exchange()).bodyJson().doesNotHavePath("$.spot");
    }

    @Test
    @DisplayName("[F-29][CM-03] 빈 JSON 객체로 고치면 200이고 아무것도 바뀌지 않으며, title·content를 null로 보내면 400이다")
    void patchEmptyObjectAndExplicitNulls() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        clock.advance(Duration.ofHours(1));
        String uri = POSTS + "/" + postId;

        // when
        MvcTestResult empty = fixture.send(mvc.patch().uri(uri), session, "{}");
        MvcTestResult nullTitle = fixture.send(mvc.patch().uri(uri), session, "{\"title\":null}");
        MvcTestResult nullContent = fixture.send(mvc.patch().uri(uri), session, "{\"content\":null}");
        MvcTestResult blankTitle = fixture.send(mvc.patch().uri(uri), session, "{\"title\":\"   \"}");
        MvcTestResult longContent =
                fixture.send(mvc.patch().uri(uri), session, "{\"content\":\"" + "가".repeat(10001) + "\"}");

        // then
        assertThat(empty).hasStatus(HttpStatus.OK);
        assertThat(empty).bodyJson().extractingPath("$.title").isEqualTo("제목");
        assertThat(empty).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-10-05T03:00:00Z");
        for (MvcTestResult rejected : new MvcTestResult[] {nullTitle, nullContent, blankTitle, longContent}) {
            assertThat(rejected).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(rejected).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(mvc.get().uri(uri).exchange())
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("제목");
    }

    @Test
    @DisplayName("[F-29][CM-03] 다른 회원이 고치거나 지우면 403 ACCESS_DENIED이고 글은 그대로다")
    void otherMemberCannotReviseOrDelete() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        Cookie otherSession = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(authorSession, "제목", "본문", null);
        String uri = POSTS + "/" + postId;

        // when
        MvcTestResult patch = fixture.send(mvc.patch().uri(uri), otherSession, "{\"title\":\"가로채기\"}");
        MvcTestResult delete = fixture.send(mvc.delete().uri(uri), otherSession, null);

        // then
        assertThat(patch).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(mvc.get().uri(uri).exchange())
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("제목");
        assertThat(fixture.postStatus(postId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 지우면 204이고 행은 DELETED로 남으며, 이후 목록에서 빠지고 상세·수정·삭제는 작성자에게도 404다")
    void deletedPostIsHiddenAndKeptAsRow() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        String uri = POSTS + "/" + postId;

        // when
        MvcTestResult delete = fixture.send(mvc.delete().uri(uri), session, null);
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(uri).exchange();
        MvcTestResult patch = fixture.send(mvc.patch().uri(uri), session, "{\"title\":\"다시\"}");
        MvcTestResult deleteAgain = fixture.send(mvc.delete().uri(uri), session, null);

        // then
        assertThat(delete).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(fixture.postStatus(postId)).isEqualTo("DELETED");
        assertThat(fixture.postCount()).isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(detail).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(detail).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(patch).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(deleteAgain).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-29][CM-03] 숨김이나 검토 대기 글은 작성자에게도 없는 글이다. 다른 회원이 건드릴 때도 403이 아니라 404다")
    void hiddenPostIsNotFoundForEveryone() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        Cookie otherSession = fixture.verifiedSession(fixture.saveMember(null));
        long hiddenId = fixture.writePost(authorSession, "숨김", "본문", null);
        long pendingId = fixture.writePost(authorSession, "검토 대기", "본문", null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hiddenId);
        jdbc.update("UPDATE community_post SET status = 'PENDING_REVIEW' WHERE id = ?", pendingId);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult hiddenDetail = mvc.get().uri(POSTS + "/" + hiddenId).exchange();
        MvcTestResult pendingPatchByAuthor =
                fixture.send(mvc.patch().uri(POSTS + "/" + pendingId), authorSession, "{}");
        MvcTestResult hiddenDeleteByOther = fixture.send(mvc.delete().uri(POSTS + "/" + hiddenId), otherSession, null);

        // then
        assertThat(list).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(hiddenDetail).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(pendingPatchByAuthor).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(hiddenDeleteByOther).hasStatus(HttpStatus.NOT_FOUND);
    }
}
