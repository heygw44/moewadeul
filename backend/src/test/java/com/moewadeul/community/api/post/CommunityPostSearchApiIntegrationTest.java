package com.moewadeul.community.api.post;

import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static com.moewadeul.community.api.post.CommunityPostApiFixture.postIds;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.community.api.CommunityApiFixture;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class CommunityPostSearchApiIntegrationTest {

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
    @DisplayName("[F-29][CM-13] 제목+내용·제목·내용·글쓴이 대상마다 검색어가 들어간 글만 나오고, 검색 대상을 보내지 않으면 제목+내용을 찾는다")
    void searchesBySearchType() {
        // given
        Member writer = fixture.saveMember("새벽능선");
        Cookie session = fixture.verifiedSession(writer);
        Cookie other = fixture.verifiedSession(fixture.saveMember("바람"));
        long inTitle = fixture.writePost(session, "굴업도 후기", "바다가 좋았다", null);
        long inContent = fixture.writePost(other, "첫 백패킹", "굴업도에 다녀왔다", null);
        long neither = fixture.writePost(other, "장비 질문", "텐트 추천", null);

        // when
        MvcTestResult defaultType = mvc.get().uri(POSTS + "?keyword=굴업도").exchange();
        MvcTestResult titleContent =
                mvc.get().uri(POSTS + "?searchType=TITLE_CONTENT&keyword=굴업도").exchange();
        MvcTestResult title =
                mvc.get().uri(POSTS + "?searchType=TITLE&keyword=굴업도").exchange();
        MvcTestResult content =
                mvc.get().uri(POSTS + "?searchType=CONTENT&keyword=굴업도").exchange();
        MvcTestResult author =
                mvc.get().uri(POSTS + "?searchType=AUTHOR&keyword=새벽").exchange();
        MvcTestResult trimmed =
                mvc.get().uri(POSTS + "?searchType=TITLE&keyword=  굴업도  ").exchange();

        // then
        postIds(defaultType).containsExactly((int) inContent, (int) inTitle);
        postIds(titleContent).containsExactly((int) inContent, (int) inTitle);
        postIds(title).containsExactly((int) inTitle);
        postIds(content).containsExactly((int) inContent);
        postIds(author).containsExactly((int) inTitle);
        postIds(trimmed).containsExactly((int) inTitle);
        postIds(defaultType).doesNotContain((int) neither);
    }

    @Test
    @DisplayName("[F-29][CM-13] 검색은 최근 ACTIVE 글 10,000개 안에서만 찾고, 활성이 아닌 글은 10,000개에 넣지 않으며, 검색하지 않는 목록은 그보다 오래된 글도 준다")
    void searchLooksOnlyAtRecentActivePosts() {
        // given
        Member author = fixture.saveMember(null);
        Cookie session = fixture.verifiedSession(author);
        long old = fixture.writePost(session, "오래된 능선 글", "본문", null);
        insertPosts(author.getId(), "HIDDEN", 3);
        insertPosts(author.getId(), "ACTIVE", CommunityPost.SEARCH_RECENT_POST_LIMIT - 2);
        long latest = fixture.writePost(session, "새 능선 글", "본문", null);

        // when
        MvcTestResult atBoundary = mvc.get().uri(POSTS + "?keyword=능선").exchange();
        insertPosts(author.getId(), "ACTIVE", 1);
        MvcTestResult pushedOut = mvc.get().uri(POSTS + "?keyword=능선").exchange();
        MvcTestResult unsearched = mvc.get()
                .uri(POSTS + "?size=1&page=" + CommunityPost.SEARCH_RECENT_POST_LIMIT)
                .exchange();

        // then
        postIds(atBoundary).containsExactly((int) latest, (int) old);
        postIds(pushedOut).containsExactly((int) latest);
        postIds(unsearched).containsExactly((int) old);
        assertThat(unsearched).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29][CM-13] 검색어의 %, _, 역슬래시는 와일드카드가 아닌 글자 그대로 찾는다")
    void searchTreatsWildcardsLiterally() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long percent = fixture.writePost(session, "할인 50% 텐트", "본문", null);
        long fifty = fixture.writePost(session, "50개 팩", "본문", null);
        long underscore = fixture.writePost(session, "a_b 매듭", "본문", null);
        long axb = fixture.writePost(session, "axb 매듭", "본문", null);
        long backslash = fixture.writePost(session, "경로 c:\\\\tent", "본문", null);
        long noBackslash = fixture.writePost(session, "경로 c:tent", "본문", null);

        // when
        MvcTestResult byPercent =
                mvc.get().uri(POSTS + "?searchType=TITLE&keyword={k}", "50%").exchange();
        MvcTestResult byUnderscore =
                mvc.get().uri(POSTS + "?searchType=TITLE&keyword={k}", "a_b").exchange();
        MvcTestResult byBackslash =
                mvc.get().uri(POSTS + "?searchType=TITLE&keyword={k}", "c:\\").exchange();

        // then
        postIds(byPercent).containsExactly((int) percent).doesNotContain((int) fifty);
        postIds(byUnderscore).containsExactly((int) underscore).doesNotContain((int) axb);
        postIds(byBackslash).containsExactly((int) backslash).doesNotContain((int) noBackslash);
    }

    @Test
    @DisplayName("[F-29][CM-13] 공백을 지운 검색어가 1자이거나 51자이면 400 INVALID_INPUT이고, 공백뿐이면 검색하지 않는다")
    void rejectsKeywordOutOfLengthAndIgnoresBlank() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        fixture.writePost(session, "가나", "본문", null);
        fixture.writePost(session, "다라", "본문", null);

        // when
        MvcTestResult one = mvc.get().uri(POSTS + "?keyword= 가 ").exchange();
        MvcTestResult tooLong =
                mvc.get().uri(POSTS + "?keyword=" + "가".repeat(51)).exchange();
        MvcTestResult fifty =
                mvc.get().uri(POSTS + "?keyword=" + "가".repeat(50)).exchange();
        MvcTestResult unknownType =
                mvc.get().uri(POSTS + "?searchType=COMMENT&keyword=가나").exchange();
        MvcTestResult blank =
                mvc.get().uri(POSTS + "?searchType=TITLE&keyword=   ").exchange();

        // then
        for (MvcTestResult rejected : new MvcTestResult[] {one, tooLong, unknownType}) {
            assertThat(rejected).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(rejected).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(one).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("keyword");
        assertThat(fifty).hasStatus(HttpStatus.OK);
        assertThat(blank).hasStatus(HttpStatus.OK);
        postIds(blank).hasSize(2);
    }

    @Test
    @DisplayName("[F-29][CM-13] 검색은 인기글·장소 조건, 페이지 번호와 함께 쓸 수 있고, 탈퇴한 작성자는 익명화된 닉네임으로 찾힌다")
    void searchCombinesWithOtherConditions() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Member writer = fixture.saveMember(null);
        Cookie session = fixture.verifiedSession(writer);
        long popularOnSpot = fixture.writePost(session, "능선 야영 후기", "본문", spotId);
        long plainOnSpot = fixture.writePost(session, "능선 야영 질문", "본문", spotId);
        long popularElsewhere = fixture.writePost(session, "능선 야영 장비", "본문", null);
        List<Cookie> likers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            likers.add(fixture.verifiedSession(fixture.saveMember(null)));
        }
        posts.like(popularOnSpot, likers);
        posts.like(popularElsewhere, likers);
        jdbc.update("UPDATE member SET nickname = ? WHERE id = ?", "탈퇴회원_" + writer.getId(), writer.getId());

        // when
        MvcTestResult popularOnly =
                mvc.get().uri(POSTS + "?keyword=능선&popular=true").exchange();
        MvcTestResult all = mvc.get()
                .uri(POSTS + "?keyword=능선&popular=true&spotId=" + spotId)
                .exchange();
        MvcTestResult paged = mvc.get().uri(POSTS + "?keyword=능선&page=1&size=2").exchange();
        MvcTestResult byWithdrawn =
                mvc.get().uri(POSTS + "?searchType=AUTHOR&keyword=탈퇴회원").exchange();

        // then
        postIds(popularOnly).containsExactly((int) popularElsewhere, (int) popularOnSpot);
        postIds(all).containsExactly((int) popularOnSpot);
        postIds(paged).containsExactly((int) popularOnSpot);
        assertThat(paged).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        postIds(byWithdrawn).containsExactly((int) popularElsewhere, (int) plainOnSpot, (int) popularOnSpot);
    }

    private void insertPosts(long memberId, String status, int count) {
        jdbc.update("""
                INSERT INTO community_post (member_id, title, content, status, created_at, updated_at)
                SELECT ?, '다른 글', '본문', ?, NOW(6), NOW(6)
                FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
                CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
                CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
                CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
                LIMIT ?
                """, memberId, status, count);
    }
}
