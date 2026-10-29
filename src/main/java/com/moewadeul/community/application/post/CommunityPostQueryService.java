package com.moewadeul.community.application.post;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.infra.post.CommunityPostListCondition;
import com.moewadeul.community.infra.post.CommunityPostMapper;
import com.moewadeul.community.infra.post.CommunityPostRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityPostItemReader communityPostItemReader;
    private final CommunityViewCountBuffer viewCountBuffer;

    @Transactional(readOnly = true)
    public CommunityPostPage list(CommunityPostListQuery query, int page, int size) {
        long offset = (long) page * size;
        List<CommunityPostRow> rows = communityPostMapper.selectActive(toCondition(query), offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<CommunityPostItem> content = rows.stream()
                .limit(size)
                .map(communityPostItemReader::toListItem)
                .toList();
        return new CommunityPostPage(content, page, size, hasNext);
    }

    @Transactional(readOnly = true)
    public CommunityPostItem detail(long postId, Long viewerId) {
        CommunityPostItem item = communityPostItemReader
                .findDetail(postId, viewerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (viewerId != null && viewerId == item.authorId()) {
            return item;
        }
        return item.withMoreViews(viewCountBuffer.add(postId, 1));
    }

    private CommunityPostListCondition toCondition(CommunityPostListQuery query) {
        Integer minLikeCount = query.popular() ? CommunityPost.POPULAR_LIKE_THRESHOLD : null;
        if (query.keyword() == null) {
            return new CommunityPostListCondition(query.spotId(), minLikeCount, null, null, null);
        }
        return new CommunityPostListCondition(
                query.spotId(),
                minLikeCount,
                query.searchType().name(),
                escapeLike(query.keyword()),
                communityPostMapper.selectActiveIdAfter(CommunityPost.SEARCH_RECENT_POST_LIMIT - 1));
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
