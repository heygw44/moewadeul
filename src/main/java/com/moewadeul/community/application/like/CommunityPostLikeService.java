package com.moewadeul.community.application.like;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.infra.like.CommunityPostLikeMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 커뮤니티 글에 좋아요를 누르거나 취소한다.
 *
 * <p>글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) NOT_FOUND로 거부한다. 이미 누른 글에 다시 누르거나 누르지 않은 글을 취소해도
 * 예외 없이 같은 결과를 돌려주므로, 호출하는 쪽이 같은 요청을 다시 보내도 된다.
 *
 * <p>좋아요 수는 글 행에 저장한다. 그래서 두 메서드는 맨 처음에 글 행을 쓰기 잠금으로 읽어 같은 글의 좋아요·취소를 한 줄로 세우고, 행을 바꾼 뒤
 * 좋아요 행을 다시 세어 글 행에 쓴다. 하나 더하거나 빼지 않는 이유는, 중복 키를 무시하는 삽입이 새 행을 넣었는지 영향 행 수로 알 수 없기
 * 때문이다(JDBC 드라이버가 이미 있던 행도 1로 센다). 잠금 순서는 언제나 글 행, 좋아요 행이라 좋아요끼리 교착 상태가 나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostLikeService {

    private final CommunityPostLikeMapper communityPostLikeMapper;
    private final Clock clock;

    /** 호출하면 memberId인 회원의 좋아요를 postId인 글에 남기고, 그 뒤의 상태를 돌려준다. */
    @Transactional
    public CommunityPostLikeResult like(long memberId, long postId) {
        lockActivePost(postId);
        communityPostLikeMapper.insertIgnoringDuplicate(postId, memberId, clock.instant());
        communityPostLikeMapper.refreshLikeCount(postId);
        return new CommunityPostLikeResult(true, communityPostLikeMapper.selectLikeCount(postId));
    }

    /** 호출하면 memberId인 회원의 좋아요를 postId인 글에서 지우고, 그 뒤의 상태를 돌려준다. */
    @Transactional
    public CommunityPostLikeResult unlike(long memberId, long postId) {
        lockActivePost(postId);
        communityPostLikeMapper.delete(postId, memberId);
        communityPostLikeMapper.refreshLikeCount(postId);
        return new CommunityPostLikeResult(false, communityPostLikeMapper.selectLikeCount(postId));
    }

    private void lockActivePost(long postId) {
        String status = communityPostLikeMapper.lockPostStatus(postId);
        if (!CommunityPostStatus.ACTIVE.name().equals(status)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }
}
