package com.moewadeul.community.api.post;

import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static com.moewadeul.community.api.post.CommunityPostApiFixture.postIds;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.community.api.CommunityApiFixture;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
class CommunityPostListApiIntegrationTest {

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
    private CommunityPostApiFixture posts;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationIssuer, jdbc);
        posts = new CommunityPostApiFixture(mvc, fixture);
    }

    @Test
    @DisplayName("[F-29][CM-09] 목록 항목은 정해진 필드만 담고, 응답 어디에도 작성자의 이메일이 없다")
    void listItemHasOnlyDefinedFieldsAndNoEmail() throws Exception {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Member author = fixture.saveMember("새벽능선");
        long postId = fixture.writePost(fixture.verifiedSession(author), "굴업도", "바다가 좋았다", spotId);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(list).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "postId": %d,
                    "title": "굴업도",
                    "excerpt": "바다가 좋았다",
                    "author": { "memberId": %d, "nickname": "새벽능선" },
                    "spot": { "spotId": %d, "name": "능선 끝 평지" },
                    "imageCount": 0,
                    "likeCount": 0,
                  "commentCount": 0,
                    "viewCount": 0,
                    "createdAt": "2026-10-05T03:00:00Z"
                  }],
                  "page": 0, "size": 20, "hasNext": false
                }
                """.formatted(postId, author.getId(), spotId));
        assertThat(list.getResponse().getContentAsString()).doesNotContain(author.getEmail());
        assertThat(detail.getResponse().getContentAsString()).doesNotContain(author.getEmail());
    }

    @Test
    @DisplayName("[F-29][CM-02] 목록의 excerpt는 본문 앞 100자이고, 상세의 content는 본문 전체다")
    void excerptIsFirstHundredCharacters() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        String content = "가".repeat(100) + "나".repeat(50);
        long postId = fixture.writePost(session, "긴 글", content, null);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content[0].excerpt").isEqualTo("가".repeat(100));
        assertThat(detail).bodyJson().extractingPath("$.content").isEqualTo(content);
    }

    @Test
    @DisplayName("[F-29] spotId로 거르면 그 장소에 연결한 글만 돌려주고, 거르지 않으면 모든 글을 한 목록으로 돌려준다")
    void filtersBySpot() {
        // given
        long spotA = fixture.insertSpot("A 장소", "ACTIVE");
        long spotB = fixture.insertSpot("B 장소", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long firstA = fixture.writePost(session, "장소 A 첫 글", "본문", spotA);
        long secondA = fixture.writePost(session, "장소 A 둘째 글", "본문", spotA);
        long onB = fixture.writePost(session, "장소 B 글", "본문", spotB);
        long noSpot = fixture.writePost(session, "장소 없는 글", "본문", null);
        long emptySpot = fixture.insertSpot("글 없는 장소", "ACTIVE");

        // when
        MvcTestResult all = mvc.get().uri(POSTS).exchange();
        MvcTestResult bySpot = mvc.get().uri(POSTS + "?spotId=" + spotA).exchange();
        MvcTestResult none = mvc.get().uri(POSTS + "?spotId=" + emptySpot).exchange();

        // then
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) noSpot, (int) onB, (int) secondA, (int) firstA);
        assertThat(all).bodyJson().doesNotHavePath("$.content[0].category");
        assertThat(bySpot)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) secondA, (int) firstA);
        assertThat(none).bodyJson().extractingPath("$.content").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-29] 최신 글부터 size건씩 주고, 다음 페이지가 있을 때만 hasNext가 true이다")
    void pagesNewestFirstWithHasNext() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long first = fixture.writePost(session, "첫 글", "본문", null);
        long second = fixture.writePost(session, "둘째 글", "본문", null);
        long third = fixture.writePost(session, "셋째 글", "본문", null);

        // when
        MvcTestResult page0 = mvc.get().uri(POSTS + "?page=0&size=2").exchange();
        MvcTestResult page1 = mvc.get().uri(POSTS + "?page=1&size=2").exchange();
        MvcTestResult exact = mvc.get().uri(POSTS + "?page=0&size=3").exchange();

        // then
        assertThat(page0)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) third, (int) second);
        assertThat(page0).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(page1)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) first);
        assertThat(page1).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(exact).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29][CM-01] 목록은 전체 글 수와 페이지 수 없이 hasNext만 주고, 마지막 페이지 너머를 요청하면 빈 목록과 hasNext=false를 준다")
    void listGivesOnlyHasNextAndHandlesPageBeyondLast() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        for (int i = 0; i < 5; i++) {
            fixture.writePost(session, "글 " + i, "본문", i < 3 ? spotId : null);
        }
        long deleted = fixture.writePost(session, "지운 글", "본문", null);
        fixture.send(mvc.delete().uri(POSTS + "/" + deleted), session, null);

        // when
        MvcTestResult first = mvc.get().uri(POSTS + "?page=0&size=2").exchange();
        MvcTestResult exact = mvc.get().uri(POSTS + "?page=0&size=5").exchange();
        MvcTestResult last = mvc.get().uri(POSTS + "?page=2&size=2").exchange();
        MvcTestResult beyond = mvc.get().uri(POSTS + "?page=7&size=2").exchange();
        MvcTestResult bySpot =
                mvc.get().uri(POSTS + "?spotId=" + spotId + "&size=2").exchange();
        MvcTestResult emptySpot =
                mvc.get().uri(POSTS + "?spotId=" + (spotId + 1000)).exchange();

        // then
        assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(first).bodyJson().doesNotHavePath("$.totalElements");
        assertThat(first).bodyJson().doesNotHavePath("$.totalPages");
        assertThat(exact).bodyJson().extractingPath("$.content").asList().hasSize(5);
        assertThat(exact).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(last).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(last).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(beyond).hasStatus(HttpStatus.OK);
        assertThat(beyond).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(beyond).bodyJson().extractingPath("$.page").isEqualTo(7);
        assertThat(beyond).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(bySpot).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(bySpot).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(emptySpot).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(emptySpot).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29] 범위를 벗어난 size는 400 INVALID_INPUT이다")
    void listRejectsBadSize() {
        // when
        MvcTestResult tooBig = mvc.get().uri(POSTS + "?size=51").exchange();
        MvcTestResult zero = mvc.get().uri(POSTS + "?size=0").exchange();

        // then
        assertThat(tooBig).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(zero).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[F-29][CM-02] 연결한 장소가 나중에 숨겨지면 글은 그대로 두고 목록과 상세에서 spot 필드만 빠진다")
    void spotFieldDisappearsWhenLinkedSpotIsHiddenLater() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", spotId);
        fixture.setSpotStatus(spotId, "HIDDEN");

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult filtered = mvc.get().uri(POSTS + "?spotId=" + spotId).exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content[0].postId").isEqualTo((int) postId);
        assertThat(list).bodyJson().doesNotHavePath("$.content[0].spot");
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().doesNotHavePath("$.spot");
        assertThat(filtered).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(filtered).bodyJson().doesNotHavePath("$.content[0].spot");
    }

    @Test
    @DisplayName("[F-29][CM-09][PV-08] 작성자가 탈퇴하면 목록과 상세에 익명 닉네임 탈퇴회원_{id}로 보인다")
    void withdrawnAuthorShowsAnonymizedNickname() {
        // given
        Member author = fixture.saveMember("새벽능선");
        long postId = fixture.writePost(fixture.verifiedSession(author), "제목", "본문", null);
        Member loaded = memberRepository.findById(author.getId()).orElseThrow();
        loaded.withdraw(MutableClock.DEFAULT_INSTANT);
        memberRepository.saveAndFlush(loaded);
        String anonymized = "탈퇴회원_" + author.getId();

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo(anonymized);
        assertThat(detail).bodyJson().extractingPath("$.author.nickname").isEqualTo(anonymized);
        assertThat(detail).bodyJson().extractingPath("$.author.memberId").isEqualTo((int) (long) author.getId());
    }

    @Test
    @DisplayName("[F-29][CM-12] 인기글 목록에는 좋아요 5개 이상인 글만 나오고, 좋아요가 취소돼 4개가 되면 빠지며, 전체 글 수도 인기글로 센다")
    void popularListKeepsOnlyPostsWithFiveOrMoreLikes() {
        // given
        Cookie author = fixture.verifiedSession(fixture.saveMember(null));
        long four = fixture.writePost(author, "좋아요 4개", "본문", null);
        long five = fixture.writePost(author, "좋아요 5개", "본문", null);
        long six = fixture.writePost(author, "좋아요 6개", "본문", null);
        fixture.writePost(author, "좋아요 없음", "본문", null);
        List<Cookie> likers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            likers.add(fixture.verifiedSession(fixture.saveMember(null)));
        }
        posts.like(four, likers.subList(0, 4));
        posts.like(five, likers.subList(0, 5));
        posts.like(six, likers);

        // when
        MvcTestResult popular = mvc.get().uri(POSTS + "?popular=true").exchange();
        MvcTestResult paged = mvc.get().uri(POSTS + "?popular=true&size=1").exchange();
        fixture.send(mvc.delete().uri(POSTS + "/" + five + "/like"), likers.get(0), null);
        MvcTestResult afterUnlike = mvc.get().uri(POSTS + "?popular=true").exchange();
        MvcTestResult all = mvc.get().uri(POSTS + "?popular=false").exchange();

        // then
        assertThat(popular)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) six, (int) five);
        assertThat(paged).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(afterUnlike)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) six);
        postIds(all).hasSize(4);
    }

    @Test
    @DisplayName("[F-29][CM-01][CM-12] 활성이 아닌 글과 숨긴 장소의 글이 섞여도 조건마다 hasNext를 따라 페이지를 끝까지 넘기면 맞는 글이 빠짐없이 최신순으로 나온다")
    void pagingThroughEveryConditionReturnsEveryMatchNewestFirst() throws Exception {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie ridge = fixture.verifiedSession(fixture.saveMember("능선러"));
        Cookie other = fixture.verifiedSession(fixture.saveMember(null));
        List<Cookie> likers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            likers.add(fixture.verifiedSession(fixture.saveMember(null)));
        }
        long p1 = fixture.writePost(ridge, "글 1", "본문", spotId);
        long p2 = fixture.writePost(other, "글 2", "본문", null);
        long deleted = fixture.writePost(ridge, "지운 글", "본문", null);
        long hidden = fixture.writePost(other, "숨긴 글", "본문", spotId);
        long pending = fixture.writePost(ridge, "검토 대기 글", "본문", null);
        long p6 = fixture.writePost(other, "글 6", "본문", null);
        long p7 = fixture.writePost(ridge, "글 7", "본문", spotId);
        posts.like(hidden, likers);
        posts.like(p6, likers);
        posts.like(p7, likers);
        fixture.send(mvc.delete().uri(POSTS + "/" + deleted), ridge, null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hidden);
        jdbc.update("UPDATE community_post SET status = 'PENDING_REVIEW' WHERE id = ?", pending);
        fixture.setSpotStatus(spotId, "HIDDEN");

        // when
        List<Integer> all = idsOverAllPages("");
        List<Integer> bySpot = idsOverAllPages("&spotId=" + spotId);
        List<Integer> popular = idsOverAllPages("&popular=true");
        List<Integer> byAuthor = idsOverAllPages("&searchType=AUTHOR&keyword=능선러");

        // then
        assertThat(all).containsExactly((int) p7, (int) p6, (int) p2, (int) p1);
        assertThat(bySpot).containsExactly((int) p7, (int) p1);
        assertThat(popular).containsExactly((int) p7, (int) p6);
        assertThat(byAuthor).containsExactly((int) p7, (int) p1);
    }

    private List<Integer> idsOverAllPages(String condition) throws Exception {
        List<Integer> ids = new ArrayList<>();
        for (int page = 0; ; page++) {
            MvcTestResult result =
                    mvc.get().uri(POSTS + "?size=1&page=" + page + condition).exchange();
            assertThat(result).hasStatus(HttpStatus.OK);
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            List<Integer> content = JsonPath.read(body, "$.content[*].postId");
            assertThat(content).hasSize(1);
            ids.addAll(content);
            if (!JsonPath.<Boolean>read(body, "$.hasNext")) {
                return ids;
            }
        }
    }
}
