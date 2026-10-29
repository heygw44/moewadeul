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
 * 좋아요 수를 하나 더하거나 빼지 않고 좋아요 행을 다시 세어 쓴다. 중복 키를 무시하는 삽입이 새 행을 넣었는지 영향 행 수로 알 수 없기
 * 때문이다(JDBC 드라이버가 이미 있던 행도 1로 센다).
 */
@Service
@RequiredArgsConstructor
public class CommunityPostLikeService {

    private final CommunityPostLikeMapper communityPostLikeMapper;
    private final Clock clock;

    @Transactional
    public CommunityPostLikeResult like(long memberId, long postId) {
        lockActivePost(postId);
        communityPostLikeMapper.insertIgnoringDuplicate(postId, memberId, clock.instant());
        communityPostLikeMapper.refreshLikeCount(postId);
        return new CommunityPostLikeResult(true, communityPostLikeMapper.selectLikeCount(postId));
    }

    @Transactional
    public CommunityPostLikeResult unlike(long memberId, long postId) {
        lockActivePost(postId);
        communityPostLikeMapper.delete(postId, memberId);
        communityPostLikeMapper.refreshLikeCount(postId);
        return new CommunityPostLikeResult(false, communityPostLikeMapper.selectLikeCount(postId));
    }

    private void lockActivePost(long postId) {
        if (communityPostLikeMapper.lockPostStatus(postId) != CommunityPostStatus.ACTIVE) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }
}
