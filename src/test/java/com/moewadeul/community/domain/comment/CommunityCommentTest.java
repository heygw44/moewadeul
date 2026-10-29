package com.moewadeul.community.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.moderation.ModerationTransition;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.community.domain.CommunityErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class CommunityCommentTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    @DisplayName("[F-29][CM-04] 댓글을 만들면 ACTIVE 상태로 값을 담고 작성·수정 시각은 만든 시각이다")
    void writeKeepsValuesAsActive() {
        // when
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "좋은 글이에요", NOW);

        // then
        assertThat(comment.getPostId()).isEqualTo(3L);
        assertThat(comment.getMemberId()).isEqualTo(7L);
        assertThat(comment.getParentId()).isNull();
        assertThat(comment.getContent()).isEqualTo("좋은 글이에요");
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.ACTIVE);
        assertThat(comment.isActive()).isTrue();
        assertThat(comment.getCreatedAt()).isEqualTo(NOW);
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-29][CM-04] 부모 댓글 ID가 있으면 답글이고, 없으면 최상위 댓글이다")
    void isReplyDependsOnParentId() {
        // when
        CommunityComment top = CommunityComment.write(3L, 7L, null, "댓글", NOW);
        CommunityComment reply = CommunityComment.write(3L, 7L, 11L, "답글", NOW);

        // then
        assertThat(top.isReply()).isFalse();
        assertThat(reply.isReply()).isTrue();
        assertThat(reply.getParentId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("[F-29][CM-04] 최상위 댓글에 답하면 그 댓글이 부모이고 답한 대상은 따로 남기지 않는다")
    void replyToTopLevelUsesItAsParent() {
        // given
        CommunityComment top = saved(CommunityComment.write(3L, 7L, null, "댓글", NOW), 11L);

        // when
        CommunityComment reply = CommunityComment.replyTo(top, 8L, "답글", NOW);

        // then
        assertThat(reply.getPostId()).isEqualTo(3L);
        assertThat(reply.getMemberId()).isEqualTo(8L);
        assertThat(reply.getParentId()).isEqualTo(11L);
        assertThat(reply.getReplyToCommentId()).isNull();
        assertThat(reply.isActive()).isTrue();
    }

    @Test
    @DisplayName("[F-29][CM-04] 답글에 답하면 깊이를 늘리지 않고 그 답글의 부모를 부모로 쓰며, 답한 답글을 대상으로 남긴다")
    void replyToReplyKeepsOneLevel() {
        // given
        CommunityComment reply = saved(CommunityComment.write(3L, 7L, 11L, "답글", NOW), 12L);

        // when
        CommunityComment answer = CommunityComment.replyTo(reply, 9L, "답글에 답해요", NOW);

        // then
        assertThat(answer.getPostId()).isEqualTo(3L);
        assertThat(answer.getParentId()).isEqualTo(11L);
        assertThat(answer.getReplyToCommentId()).isEqualTo(12L);
        assertThat(answer.isReply()).isTrue();
    }

    @Test
    @DisplayName("[F-29] 답글을 만들 때도 내용이 공백뿐이거나 1000자를 넘으면 거부한다")
    void replyToRejectsInvalidContent() {
        // given
        CommunityComment top = saved(CommunityComment.write(3L, 7L, null, "댓글", NOW), 11L);

        // then
        assertThatThrownBy(() -> CommunityComment.replyTo(top, 8L, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityComment.replyTo(top, 8L, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29] 댓글 내용 1000자는 받고 1001자는 거부한다")
    void contentLengthBoundary() {
        // when
        CommunityComment atLimit = CommunityComment.write(3L, 7L, null, "가".repeat(1000), NOW);

        // then
        assertThat(atLimit.getContent()).hasSize(1000);
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", "   \n\t"})
    @DisplayName("[F-29] 내용이 비었거나 공백뿐이면 댓글을 만들지도 고치지도 못한다")
    void blankContentIsRejected(String content) {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // then
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, content, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comment.revise(content, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(comment.getContent()).isEqualTo("댓글");
    }

    @Test
    @DisplayName("[F-29] 내용이 null이거나 시각이 null이면 거부한다")
    void nullArgumentsAreRejected() {
        // then
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, "댓글", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29] 댓글을 고치면 내용과 수정 시각만 바뀐다")
    void reviseChangesContentAndUpdatedAt() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // when
        comment.revise("고친 댓글", LATER);

        // then
        assertThat(comment.getContent()).isEqualTo("고친 댓글");
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
        assertThat(comment.getCreatedAt()).isEqualTo(NOW);
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.ACTIVE);
    }

    @Test
    @DisplayName("[F-29][CM-03] 댓글을 지우면 DELETED가 되고 내용이 null로 지워진다")
    void deleteClearsContent() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // when
        comment.delete(LATER);

        // then
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.DELETED);
        assertThat(comment.isActive()).isFalse();
        assertThat(comment.getContent()).isNull();
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[F-29][CM-03] 이미 지운 댓글을 다시 지워도 수정 시각이 바뀌지 않는다")
    void deleteTwiceKeepsFirstDeletionTime() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);
        comment.delete(LATER);

        // when
        comment.delete(LATER.plusSeconds(60));

        // then
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityCommentStatus.class,
            names = {"HIDDEN", "PENDING_REVIEW"})
    @DisplayName("[F-29][CM-03] 숨겼거나 검토 대기인 댓글은 지울 수 없고 COMMUNITY_INVALID_STATE이며 상태와 내용은 그대로다")
    void deleteRejectsHiddenOrPendingReview(CommunityCommentStatus status) {
        // given
        CommunityComment comment = commentIn(status);

        // when // then
        assertThatThrownBy(() -> comment.delete(LATER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommunityErrorCode.COMMUNITY_INVALID_STATE));
        assertThat(comment.getStatus()).isEqualTo(status);
        assertThat(comment.getContent()).isEqualTo("내용");
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-29] 작성자가 아니면 requireWrittenBy가 ACCESS_DENIED이고, 글 ID를 비교한다")
    void requireWrittenByAndBelongsTo() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // then
        assertThatCode(() -> comment.requireWrittenBy(7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> comment.requireWrittenBy(8L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
        assertThat(comment.belongsTo(3L)).isTrue();
        assertThat(comment.belongsTo(4L)).isFalse();
    }

    private CommunityComment commentIn(CommunityCommentStatus status) {
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "내용", NOW);
        switch (status) {
            case ACTIVE -> {}
            case PENDING_REVIEW -> comment.receiveReport(ModerationTransition.REPORT_THRESHOLD, NOW);
            case HIDDEN -> comment.hide(NOW);
            case DELETED -> comment.delete(NOW);
        }
        return comment;
    }

    @Test
    @DisplayName("[F-29][CM-07] 검토 전 신고가 4건이면 ACTIVE 댓글은 그대로이고, 5건이 되면 검토 대기로 바뀌며 수정 시각이 바뀐다")
    void receiveReportMovesToPendingReviewAtFive() {
        // given
        CommunityComment comment = commentIn(CommunityCommentStatus.ACTIVE);

        // when
        comment.receiveReport(4, NOW.plusSeconds(1));

        // then
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.ACTIVE);
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);

        // when
        comment.receiveReport(5, LATER);

        // then
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.PENDING_REVIEW);
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
        assertThat(comment.isActive()).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityCommentStatus.class,
            names = {"PENDING_REVIEW", "HIDDEN", "DELETED"})
    @DisplayName("[F-29][CM-07] ACTIVE가 아닌 댓글의 검토 전 신고가 5건이 되면 IllegalStateException을 던진다")
    void receiveReportRejectsNonActive(CommunityCommentStatus status) {
        // given
        CommunityComment comment = commentIn(status);

        // when // then
        assertThatThrownBy(() -> comment.receiveReport(5, LATER)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityCommentStatus.class,
            names = {"ACTIVE", "PENDING_REVIEW"})
    @DisplayName("[F-29][CM-08] ACTIVE나 검토 대기 댓글을 숨기면 HIDDEN이 되고 수정 시각이 바뀐다")
    void hideFromActiveOrPending(CommunityCommentStatus status) {
        // given
        CommunityComment comment = commentIn(status);

        // when
        comment.hide(LATER);

        // then
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.HIDDEN);
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityCommentStatus.class,
            names = {"HIDDEN", "DELETED"})
    @DisplayName("[F-29][CM-08] 이미 숨겼거나 작성자가 지운 댓글을 숨기면 COMMUNITY_INVALID_STATE로 거부한다")
    void hideRejectsHiddenOrDeleted(CommunityCommentStatus status) {
        // given
        CommunityComment comment = commentIn(status);

        // when // then
        assertThatThrownBy(() -> comment.hide(LATER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommunityErrorCode.COMMUNITY_INVALID_STATE));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityCommentStatus.class,
            names = {"PENDING_REVIEW", "HIDDEN"})
    @DisplayName("[F-29][CM-08] 검토 대기나 숨긴 댓글을 복구하면 ACTIVE가 되고 수정 시각이 바뀐다")
    void restoreFromPendingOrHidden(CommunityCommentStatus status) {
        // given
        CommunityComment comment = commentIn(status);

        // when
        comment.restore(LATER);

        // then
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.ACTIVE);
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityCommentStatus.class,
            names = {"ACTIVE", "DELETED"})
    @DisplayName("[F-29][CM-08] 이미 ACTIVE이거나 작성자가 지운 댓글을 복구하면 COMMUNITY_INVALID_STATE로 거부한다")
    void restoreRejectsActiveOrDeleted(CommunityCommentStatus status) {
        // given
        CommunityComment comment = commentIn(status);

        // when // then
        assertThatThrownBy(() -> comment.restore(LATER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommunityErrorCode.COMMUNITY_INVALID_STATE));
    }

    private static CommunityComment saved(CommunityComment comment, long id) {
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }
}
