package com.moewadeul.community.application.comment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.comment.CommunityComment;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.community.infra.comment.CommunityCommentMapper;
import com.moewadeul.community.infra.comment.CommunityCommentRow;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 커뮤니티 글에 댓글과 답글을 쓰고, 자기 댓글을 고치거나 지운다.
 *
 * <p>댓글은 ACTIVE인 글에만 달 수 있다. 답할 댓글은 같은 글의 ACTIVE 댓글이어야 하고, 어기면 INVALID_INPUT으로 거부한다. 답할 댓글이
 * 답글이어도 받는다. 이때 새 답글은 그 답글의 최상위 댓글 아래에 달리므로, 최상위 댓글의 상태는 보지 않는다.
 * 고치거나 지울 때는 댓글이 없거나, 댓글이 ACTIVE가 아니거나, 글이 ACTIVE가 아니면 NOT_FOUND로 거부한다. 작성자 본인에게도 같다.
 * 그 조건을 통과한 뒤 작성자가 아니면 ACCESS_DENIED로 거부하므로, 검사 순서는 대상 확인, 작성자 확인이다.
 *
 * <p>댓글을 지울 때는 상태를 DELETED로 바꾸고 내용을 지운 채 행을 남긴다. 답글이 달려 있는지는 따지지 않는다.
 * 답글이 있는 댓글은 목록에서 자리만 남기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class CommunityCommentCommandService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentMapper communityCommentMapper;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 댓글을 postId인 글에 저장하고 댓글 ID를 돌려준다.
     *
     * <p>parentId는 답할 댓글이고, 최상위 댓글과 답글 모두 될 수 있다. 글이 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다. 내용이 공백뿐이거나
     * 1,000자를 넘거나, parentId인 댓글이 없거나 다른 글의 댓글이거나 ACTIVE가 아니면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public long write(long memberId, long postId, CommunityCommentWriteCommand command) {
        requireActivePost(postId);
        requireValidContent(command.content());
        CommunityComment comment = command.parentId() == null
                ? CommunityComment.write(postId, memberId, null, command.content(), clock.instant())
                : CommunityComment.replyTo(
                        loadReplyTarget(postId, command.parentId()), memberId, command.content(), clock.instant());
        return communityCommentRepository.saveAndFlush(comment).getId();
    }

    /**
     * 호출하면 작성자 memberId가 commentId인 댓글의 내용을 바꾸고, 바꾼 뒤의 댓글을 돌려준다. 최상위 댓글이면 ACTIVE 답글도 함께 담는다.
     *
     * <p>댓글이나 글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 다른 회원이 쓴 댓글이면 ACCESS_DENIED로 거부한다. 내용이 공백뿐이거나
     * 1,000자를 넘으면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public CommunityCommentItem revise(long memberId, long commentId, String content) {
        CommunityComment comment = loadOwnActiveComment(memberId, commentId);
        requireValidContent(content);
        comment.revise(content, clock.instant());
        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.
        communityCommentRepository.flush();
        CommunityCommentRow row = communityCommentMapper
                .selectById(commentId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return CommunityCommentItem.from(row, repliesOf(comment));
    }

    /**
     * 호출하면 작성자 memberId의 댓글을 DELETED로 바꾸고 내용을 지운다. 행은 남는다.
     *
     * <p>댓글이나 글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 다른 회원이 쓴 댓글이면 ACCESS_DENIED로 거부한다.
     */
    @Transactional
    public void delete(long memberId, long commentId) {
        loadOwnActiveComment(memberId, commentId).delete(clock.instant());
    }

    private List<CommunityCommentItem> repliesOf(CommunityComment comment) {
        if (comment.isReply()) {
            return List.of();
        }
        return communityCommentMapper.selectActiveReplies(List.of(comment.getId())).stream()
                .map(row -> CommunityCommentItem.from(row, List.of()))
                .toList();
    }

    private CommunityComment loadOwnActiveComment(long memberId, long commentId) {
        CommunityComment comment = communityCommentRepository
                .findById(commentId)
                .filter(CommunityComment::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        requireActivePost(comment.getPostId());
        if (!comment.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return comment;
    }

    private void requireActivePost(long postId) {
        communityPostRepository
                .findById(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 답할 댓글은 같은 글에 있고 내용이 보여야 한다. 답글이어도 되는데, 새 답글의 부모는 엔티티가 최상위 댓글로 맞추기 때문이다.
    private CommunityComment loadReplyTarget(long postId, long targetId) {
        return communityCommentRepository
                .findById(targetId)
                .filter(target -> target.belongsTo(postId) && target.isActive())
                .orElseThrow(() -> invalidInput("답글을 달 수 없는 댓글이에요."));
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank()) {
            throw invalidInput("댓글을 입력해 주세요.");
        }
        if (content.length() > CommunityComment.CONTENT_MAX_LENGTH) {
            throw invalidInput("댓글은 " + CommunityComment.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
