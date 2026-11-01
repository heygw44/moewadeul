package com.moewadeul.community.application.comment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.community.infra.comment.CommunityCommentMapper;
import com.moewadeul.community.infra.comment.CommunityCommentRow;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityCommentQueryService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentMapper communityCommentMapper;

    @Transactional(readOnly = true)
    public PageSlice<CommunityCommentItem> list(long postId, int page, int size) {
        requireActivePost(postId);
        List<CommunityCommentRow> rows = communityCommentMapper.selectTopLevel(
                postId, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<CommunityCommentRow> parents = PageSlice.of(rows, page, size);
        return new PageSlice<>(toItems(parents.content()), page, size, parents.hasNext());
    }

    private void requireActivePost(long postId) {
        communityPostRepository
                .findById(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(BusinessException::notFound);
    }

    private List<CommunityCommentItem> toItems(List<CommunityCommentRow> parents) {
        if (parents.isEmpty()) {
            return List.of();
        }
        List<Long> parentIds =
                parents.stream().map(CommunityCommentRow::commentId).toList();
        Map<Long, List<CommunityCommentItem>> repliesByParent =
                communityCommentMapper.selectActiveReplies(parentIds).stream()
                        .collect(Collectors.groupingBy(
                                CommunityCommentRow::parentId,
                                Collectors.mapping(
                                        row -> CommunityCommentItem.from(row, List.of()), Collectors.toList())));
        return parents.stream()
                .map(row -> CommunityCommentItem.from(row, repliesByParent.getOrDefault(row.commentId(), List.of())))
                .toList();
    }
}
