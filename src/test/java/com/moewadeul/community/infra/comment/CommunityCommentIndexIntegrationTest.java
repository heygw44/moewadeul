package com.moewadeul.community.infra.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CommunityCommentIndexIntegrationTest {

    private static final String ACTIVE_COUNT_SQL =
            "SELECT COUNT(*) FROM community_comment c WHERE c.post_id = ? AND c.status = 'ACTIVE'";

    private static final String TOP_LEVEL_SQL = "SELECT c.id FROM community_comment c"
            + " WHERE c.post_id = ? AND c.parent_id IS NULL ORDER BY c.id ASC LIMIT 21";

    private static final int COMMENTS_PER_POST = 300;

    @Autowired
    private CommunityCommentMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-29][CM-01] 글 목록의 댓글 수는 (post_id, status) 인덱스만 읽고 센다")
    void activeCommentCountReadsOnlyPostStatusIndex() {
        // given
        long busy = seedPostsWithComments();
        jdbc.execute("ANALYZE TABLE community_comment");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + ACTIVE_COUNT_SQL, busy);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_community_comment_post_status");
        assertThat((String) plan.get("Extra")).contains("Using index");
        assertThat(jdbc.queryForObject(ACTIVE_COUNT_SQL, Long.class, busy)).isEqualTo(COMMENTS_PER_POST * 9 / 10);
    }

    @Test
    @DisplayName("[F-29][CM-04] 댓글 목록은 (post_id, parent_id) 인덱스로 글 한 건의 최상위 댓글을 id 순서대로 정렬 없이 읽는다")
    void topLevelCommentsAreReadInIdOrderThroughPostParentIndex() {
        // given
        long busy = seedPostsWithComments();
        jdbc.execute("ANALYZE TABLE community_comment");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + TOP_LEVEL_SQL, busy);
        List<CommunityCommentRow> firstPage = mapper.selectTopLevel(busy, 0, 21);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_community_comment_post_parent");
        assertThat((String) plan.get("Extra")).doesNotContain("filesort");
        assertThat(firstPage)
                .extracting(CommunityCommentRow::commentId)
                .isSorted()
                .hasSize(21);
    }

    private long seedPostsWithComments() {
        long member = new ProgramApplyFixture(jdbc, memberRepository).saveMember();
        long busy = insertPost(member);
        long other = insertPost(member);
        List<Object[]> rows = new ArrayList<>();
        for (long post : List.of(busy, other)) {
            for (int i = 0; i < COMMENTS_PER_POST; i++) {
                rows.add(new Object[] {post, member, i % 10 == 0 ? "DELETED" : "ACTIVE"});
            }
        }
        jdbc.batchUpdate(
                "INSERT INTO community_comment (post_id, member_id, content, status, created_at, updated_at)"
                        + " VALUES (?, ?, '댓글', ?, NOW(6), NOW(6))",
                rows);
        return busy;
    }

    private long insertPost(long member) {
        jdbc.update(
                "INSERT INTO community_post (member_id, title, content, status, created_at, updated_at)"
                        + " VALUES (?, '제목', '본문', 'ACTIVE', NOW(6), NOW(6))",
                member);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
