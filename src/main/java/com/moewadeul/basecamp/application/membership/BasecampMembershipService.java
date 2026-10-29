package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.basecamp.domain.KickReason;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampMembershipService {

    private final BasecampRepository basecampRepository;
    private final TrustSummaryService trustSummaryService;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public void leave(long basecampId, long memberId) {
        Basecamp basecamp = lock(basecampId);
        if (trustSummaryService.detail(memberId).trustLevel() < JoinEligibilityPolicy.BASE_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        basecamp.leave(memberId, clock.instant());
    }

    @Transactional
    public void kick(long basecampId, long targetMemberId, long leaderId, KickReason reason) {
        Basecamp basecamp = lock(basecampId);
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        basecamp.kick(targetMemberId, reason, clock.instant());
        outboxEventRecorder.recordAll(basecamp.pullEvents());
    }

    // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
    // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 변경이 보이지 않는다.
    private Basecamp lock(long basecampId) {
        return basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
