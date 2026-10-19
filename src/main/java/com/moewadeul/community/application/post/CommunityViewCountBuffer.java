package com.moewadeul.community.application.post;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 아직 DB에 더하지 않은 글별 조회 수를 메모리에 모은다. {@link CommunityViewCountFlusher}가 주기적으로 꺼내 DB에 더한다.
 *
 * <p>예전에는 상세 조회마다 글 행을 UPDATE해서, 같은 글의 조회가 그 행 잠금을 커밋까지 하나씩 기다렸다. 글 하나에 조회가 몰리면 초당 약 450건이
 * 한계였고, 같은 행을 잠그는 좋아요도 함께 기다렸다. 그래서 조회는 메모리에서 세고, DB에는 글마다 모아서 한 번에 더한다.
 *
 * <p>더하기와 꺼내기는 {@link ConcurrentHashMap}의 키 단위 원자 연산(merge, remove)이라서, 꺼내는 사이에 들어온 조회를 잃지 않는다.
 * 꺼낸 뒤 들어온 조회는 새 값으로 다시 쌓여 다음 회차에 나간다. 이 수는 이 서버의 메모리에만 있으므로, 서버가 비정상 종료하면 아직 꺼내지 않은 수를
 * 잃는다.
 */
@Component
class CommunityViewCountBuffer {

    private final ConcurrentHashMap<Long, Long> pendingViews = new ConcurrentHashMap<>();

    /** 호출하면 그 글의 조회를 views만큼 더하고, 더한 뒤 아직 DB에 더하지 않은 그 글의 조회 수를 돌려준다. */
    long add(long postId, long views) {
        return pendingViews.merge(postId, views, Long::sum);
    }

    /** 호출하면 쌓인 조회 수를 모두 꺼내 비우고, 글 ID별 수로 돌려준다. */
    Map<Long, Long> drain() {
        Map<Long, Long> drained = new HashMap<>();
        for (Long postId : pendingViews.keySet()) {
            Long views = pendingViews.remove(postId);
            if (views != null) {
                drained.put(postId, views);
            }
        }
        return drained;
    }
}
