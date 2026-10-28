package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampApplicationCancelService {

    private final BasecampRepository basecampRepository;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    @Transactional
    public void cancel(long basecampId, long memberId) {
        // 승인이나 거절과 겹쳐도 한 번에 하나만 처리하려고 베이스캠프 행을 잠근다. 이 잠금이 트랜잭션의 첫 조회여야 한다.
        // MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면 그 시점에 스냅숏이 정해져서, 잠금을 얻은 뒤에도
        // 다른 트랜잭션이 커밋한 변경이 보이지 않기 때문이다.
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (trustSummaryService.detail(memberId).trustLevel() < JoinEligibilityPolicy.BASE_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        basecamp.cancelApplication(memberId, clock.instant());
    }
}
