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

@Service
@RequiredArgsConstructor
public class CommunityCommentCommandService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentMapper communityCommentMapper;
    private final Clock clock;

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
                .findByIdForUpdate(commentId)
                .filter(CommunityComment::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        requireActivePost(comment.getPostId());
        comment.requireWrittenBy(memberId);
        return comment;
    }

    private void requireActivePost(long postId) {
        communityPostRepository
                .findById(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

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
