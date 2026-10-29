package com.moewadeul.community.api.post;

import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.community.api.CommunityApiFixture;
import com.moewadeul.community.application.post.CommunityViewCountFlusher;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
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
class CommunityPostViewCountApiIntegrationTest {

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
    private CommunityViewCountFlusher viewCountFlusher;

    private CommunityApiFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationIssuer, jdbc);
    }

    @Test
    @DisplayName("[F-29][CM-11] 비회원과 다른 회원이 상세를 열 때마다 조회수가 1씩 오르고, DB에 더한 뒤에는 목록에도 같은 값이 보인다")
    void detailByOthersIncrementsViewCount() {
        // given
        Cookie author = fixture.verifiedSession(fixture.saveMember(null));
        Cookie other = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(author, "제목", "본문", null);

        // when
        MvcTestResult anonymous = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult member = mvc.get().uri(POSTS + "/" + postId).cookie(other).exchange();
        MvcTestResult again = mvc.get().uri(POSTS + "/" + postId).cookie(other).exchange();
        MvcTestResult listBeforeFlush = mvc.get().uri(POSTS).exchange();
        viewCountFlusher.flush();
        MvcTestResult list = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(anonymous).bodyJson().extractingPath("$.viewCount").isEqualTo(1);
        assertThat(member).bodyJson().extractingPath("$.viewCount").isEqualTo(2);
        assertThat(again).bodyJson().extractingPath("$.viewCount").isEqualTo(3);
        assertThat(listBeforeFlush)
                .bodyJson()
                .extractingPath("$.content[0].viewCount")
                .isEqualTo(0);
        assertThat(list).bodyJson().extractingPath("$.content[0].viewCount").isEqualTo(3);
        assertThat(viewCount(postId)).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-29][CM-11] 작성자가 자기 글을 열거나 고쳐도 조회수는 그대로다")
    void authorViewDoesNotCount() {
        // given
        Cookie author = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(author, "제목", "본문", null);

        // when
        MvcTestResult detail =
                mvc.get().uri(POSTS + "/" + postId).cookie(author).exchange();
        MvcTestResult revised = fixture.send(mvc.patch().uri(POSTS + "/" + postId), author, "{\"title\":\"새 제목\"}");

        // then
        assertThat(detail).bodyJson().extractingPath("$.viewCount").isEqualTo(0);
        assertThat(revised).bodyJson().extractingPath("$.viewCount").isEqualTo(0);
        assertThat(viewCount(postId)).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-11] 404가 되는 글(삭제·숨김·검토 대기)은 상세를 열어도 조회수를 세지 않는다")
    void hiddenOrDeletedPostIsNotCounted() {
        // given
        Cookie author = fixture.verifiedSession(fixture.saveMember(null));
        long deleted = fixture.writePost(author, "지운 글", "본문", null);
        long hidden = fixture.writePost(author, "숨긴 글", "본문", null);
        long pending = fixture.writePost(author, "검토 대기 글", "본문", null);
        fixture.send(mvc.delete().uri(POSTS + "/" + deleted), author, null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hidden);
        jdbc.update("UPDATE community_post SET status = 'PENDING_REVIEW' WHERE id = ?", pending);

        // when
        for (long postId : new long[] {deleted, hidden, pending}) {
            assertThat(mvc.get().uri(POSTS + "/" + postId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        }

        // then
        for (long postId : new long[] {deleted, hidden, pending}) {
            assertThat(viewCount(postId)).isZero();
        }
    }

    private int viewCount(long postId) {
        return jdbc.queryForObject("SELECT view_count FROM community_post WHERE id = ?", Integer.class, postId);
    }
}
