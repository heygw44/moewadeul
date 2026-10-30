package com.moewadeul.community.application.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.community.application.post.CommunityPostCommandService;
import com.moewadeul.community.application.post.CommunityPostWriteCommand;
import com.moewadeul.community.application.report.CommunityReportCommand;
import com.moewadeul.community.application.report.CommunityReportService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class CommunityCommentPostLockIntegrationTest {

    private static final List<String> GONE_STATUSES = List.of("HIDDEN", "DELETED");
    private static final CommunityReportCommand REPORT = new CommunityReportCommand(ContentReportReason.SPAM, "광고입니다");

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private CommunityCommentCommandService commentCommandService;

    @Autowired
    private CommunityReportService reportService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-04] 관리자가 글을 숨기거나 작성자가 글을 지우는 트랜잭션이 끝나기 전에 들어온 댓글 작성은 커밋 뒤 404로 거절되고 댓글 행을 남기지 않는다")
    void commentWriteArrivingWhilePostIsGoneIsRejected() throws Exception {
        for (String status : GONE_STATUSES) {
            // given
            long memberId = memberFixture.save().getId();
            long postId = savePost(memberId);

            // when
            Future<?> result = null;
            try (PostHold hold = new PostHold(postId, status)) {
                result = hold.submit(() ->
                        commentCommandService.write(memberId, postId, new CommunityCommentWriteCommand("댓글", null)));
                hold.assertWaitingForPostRow(result);
            }

            // then
            assertNotFound(result);
            assertThat(commentCount(postId)).as(status).isZero();
        }
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-04] 글을 숨기거나 지우는 트랜잭션이 끝나기 전에 들어온 댓글 수정은 커밋 뒤 404로 거절되고 내용이 바뀌지 않는다")
    void commentReviseArrivingWhilePostIsGoneIsRejected() throws Exception {
        for (String status : GONE_STATUSES) {
            // given
            long memberId = memberFixture.save().getId();
            long postId = savePost(memberId);
            long commentId =
                    commentCommandService.write(memberId, postId, new CommunityCommentWriteCommand("댓글", null));

            // when
            Future<?> result = null;
            try (PostHold hold = new PostHold(postId, status)) {
                result = hold.submit(() -> commentCommandService.revise(memberId, commentId, "고친 댓글"));
                hold.assertWaitingForPostRow(result);
            }

            // then
            assertNotFound(result);
            assertThat(commentContent(commentId)).as(status).isEqualTo("댓글");
        }
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-04] 글을 숨기거나 지우는 트랜잭션이 끝나기 전에 들어온 댓글 삭제는 커밋 뒤 404로 거절되고 댓글은 ACTIVE로 남는다")
    void commentDeleteArrivingWhilePostIsGoneIsRejected() throws Exception {
        for (String status : GONE_STATUSES) {
            // given
            long memberId = memberFixture.save().getId();
            long postId = savePost(memberId);
            long commentId =
                    commentCommandService.write(memberId, postId, new CommunityCommentWriteCommand("댓글", null));

            // when
            Future<?> result = null;
            try (PostHold hold = new PostHold(postId, status)) {
                result = hold.submit(() -> {
                    commentCommandService.delete(memberId, commentId);
                    return null;
                });
                hold.assertWaitingForPostRow(result);
            }

            // then
            assertNotFound(result);
            assertThat(commentStatus(commentId)).as(status).isEqualTo("ACTIVE");
        }
    }

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-07] 글을 숨기거나 지우는 트랜잭션이 끝나기 전에 들어온 댓글 신고는 커밋 뒤 404로 거절되고 신고 행을 남기지 않는다")
    void commentReportArrivingWhilePostIsGoneIsRejected() throws Exception {
        for (String status : GONE_STATUSES) {
            // given
            long authorId = memberFixture.save().getId();
            long reporterId = memberFixture.save().getId();
            long postId = savePost(authorId);
            long commentId =
                    commentCommandService.write(authorId, postId, new CommunityCommentWriteCommand("댓글", null));

            // when
            Future<?> result = null;
            try (PostHold hold = new PostHold(postId, status)) {
                result = hold.submit(() -> {
                    reportService.reportComment(reporterId, commentId, REPORT);
                    return null;
                });
                hold.assertWaitingForPostRow(result);
            }

            // then
            assertNotFound(result);
            assertThat(reportCount(commentId)).as(status).isZero();
        }
    }

    private long savePost(long authorId) {
        return postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null));
    }

    private static void assertNotFound(Future<?> result) {
        assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
                .cause()
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    private int commentCount(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_comment WHERE post_id = ?", Integer.class, postId);
    }

    private String commentContent(long commentId) {
        return jdbc.queryForObject("SELECT content FROM community_comment WHERE id = ?", String.class, commentId);
    }

    private String commentStatus(long commentId) {
        return jdbc.queryForObject("SELECT status FROM community_comment WHERE id = ?", String.class, commentId);
    }

    private int reportCount(long commentId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_report WHERE target_type = 'COMMENT' AND target_id = ?",
                Integer.class,
                commentId);
    }

    private final class PostHold implements AutoCloseable {

        private final CountDownLatch commit = new CountDownLatch(1);
        private final ExecutorService executor = Executors.newFixedThreadPool(2);
        private final Future<?> holder;

        PostHold(long postId, String status) throws InterruptedException {
            CountDownLatch held = new CountDownLatch(1);
            holder = executor.submit(() -> transactionTemplate.executeWithoutResult(txStatus -> {
                jdbc.update("UPDATE community_post SET status = ? WHERE id = ?", status, postId);
                held.countDown();
                await(commit);
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        }

        <T> Future<T> submit(Callable<T> action) {
            return executor.submit(action);
        }

        void assertWaitingForPostRow(Future<?> result) throws InterruptedException {
            Thread.sleep(500);
            assertThat(result).as("글 상태를 바꾸는 트랜잭션이 잡은 글 행 잠금을 기다린다").isNotDone();
        }

        @Override
        public void close() throws Exception {
            commit.countDown();
            holder.get(10, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        private static void await(CountDownLatch latch) {
            try {
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
