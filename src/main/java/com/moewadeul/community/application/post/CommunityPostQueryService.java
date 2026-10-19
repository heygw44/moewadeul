package com.moewadeul.community.application.post;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.infra.post.CommunityPostListCondition;
import com.moewadeul.community.infra.post.CommunityPostMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 글 목록과 상세를 읽는다. ACTIVE인 글만 읽을 수 있고, 삭제·숨김·검토 대기 글은 없는 글로 본다. */
@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityPostItemReader communityPostItemReader;
    private final CommunityViewCountBuffer viewCountBuffer;

    /**
     * 호출하면 query 조건에 맞는 ACTIVE인 글을 최신순으로 한 페이지 돌려준다.
     *
     * <p>spotId로 거를 때는 그 장소가 지금 ACTIVE인지 보지 않는다. 화면이 페이지 번호를 그리려면 전체 글 수가 필요하다. 그래서 서비스는 같은
     * 조건으로 글 수를 따로 센다. 마지막 페이지 너머를 요청하면 빈 목록과 함께 전체 글 수와 페이지 수를 돌려준다.
     *
     * <p>검색어가 있으면 최근 ACTIVE 글 {@link CommunityPost#SEARCH_RECENT_POST_LIMIT}개 안에서만 찾고, 글 수도 그 안에서 센다. 범위의 가장 작은
     * 글 ID는 한 번만 구해서 목록과 글 수가 같이 쓴다. 읽기 전용 트랜잭션 하나에서 읽으므로 세 쿼리가 같은 시점의 글을 본다.
     */
    @Transactional(readOnly = true)
    public CommunityPostPage list(CommunityPostListQuery query, int page, int size) {
        CommunityPostListCondition condition = toCondition(query);
        long totalElements = communityPostMapper.countActive(condition);
        long offset = (long) page * size;
        List<CommunityPostItem> content = offset >= totalElements
                ? List.of()
                : communityPostMapper.selectActive(condition, offset, size).stream()
                        .map(communityPostItemReader::toListItem)
                        .toList();
        return CommunityPostPage.of(content, page, size, totalElements);
    }

    /**
     * 호출하면 postId인 글 한 건을 본문 전체와 이미지 목록과 함께 돌려준다. viewerId가 null이 아니면 그 회원이 좋아요를 눌렀는지(likedByMe)도 담는다.
     * 글이 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     *
     * <p>조회한 사람이 작성자가 아니면(비회원 포함) 조회수를 1 올리고, 올린 값을 응답에 담는다. 작성자가 자기 글을 열 때는 세지 않는다. 같은 사람이
     * 다시 열어도 그때마다 센다. 조회는 글 행을 바로 바꾸지 않고 메모리에 모았다가 {@link CommunityViewCountFlusher}가 몇 초마다 더한다.
     * 그래서 응답의 조회수는 DB 값에 이 서버가 아직 더하지 않은 조회 수(이번 조회 포함)를 더한 값이다.
     */
    @Transactional(readOnly = true)
    public CommunityPostItem detail(long postId, Long viewerId) {
        CommunityPostItem item = communityPostItemReader
                .findDetail(postId, viewerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (viewerId != null && viewerId == item.authorId()) {
            return item;
        }
        // 읽은 뒤 글이 숨겨져도 여기서는 센다. DB에 더할 때 ACTIVE가 아닌 글은 0행이 바뀌어 버려진다.
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

    // LIKE에서 %와 _는 와일드카드이고 \는 그 둘을 글자로 만드는 표시다. 회원이 입력한 이 글자들을 글자 그대로 찾도록 앞에 \를 붙인다.
    // \를 먼저 바꿔야 뒤에서 붙인 \가 다시 바뀌지 않는다.
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
