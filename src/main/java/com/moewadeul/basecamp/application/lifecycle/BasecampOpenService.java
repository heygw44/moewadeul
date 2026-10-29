package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampDetails;
import com.moewadeul.basecamp.domain.BasecampErrorCode;
import com.moewadeul.basecamp.domain.BasecampOpenPolicy;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.Capacity;
import com.moewadeul.basecamp.domain.JoinCondition;
import com.moewadeul.basecamp.infra.BasecampLockMapper;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.application.ActiveSpotChecker;
import com.moewadeul.trust.application.profile.TrustDetail;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.application.sanction.SanctionQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampOpenService {

    private static final int MIN_TRUST_LEVEL_TO_OPEN = 1;

    private final TrustSummaryService trustSummaryService;
    private final SanctionQueryService sanctionQueryService;
    private final ActiveSpotChecker activeSpotChecker;
    private final BasecampRepository basecampRepository;
    private final BasecampLockMapper basecampLockMapper;
    private final Clock clock;

    @Transactional
    public BasecampOpenResult open(long leaderId, BasecampOpenCommand command) {
        // 잠금이 트랜잭션의 첫 쿼리여야 한다. 그래야 아래 개수 조회가 잠금을 얻은 뒤의 스냅숏을 읽어서, 먼저 끝난 요청이 연 베이스캠프가 보인다.
        basecampLockMapper.lockMember(leaderId);
        Instant now = clock.instant();
        BasecampOpenPolicy.requireSchedule(
                command.startDate(), command.endDate(), LocalDate.ofInstant(now, KoreaTime.ZONE));
        Capacity capacity = Capacity.of(command.capacity());
        TrustDetail trust = trustSummaryService.detail(leaderId);
        requireTrustLevel(trust);
        requireNotSuspended(leaderId);
        JoinCondition joinCondition = JoinConditions.from(command.joinCondition(), trust);
        requireNotWarningSpot(command.spotId());
        BasecampOpenPolicy.requireUnderOpenLimit(
                basecampRepository.countByLeaderIdAndStatusIn(leaderId, BasecampOpenPolicy.OPEN_COUNTED_STATUSES));

        BasecampDetails details = new BasecampDetails(
                command.title(),
                command.description(),
                command.startDate(),
                command.endDate(),
                capacity,
                joinCondition);
        Basecamp basecamp = basecampRepository.saveAndFlush(open(leaderId, command.spotId(), details, now));
        return new BasecampOpenResult(basecamp.getId(), basecamp.getStatus().name());
    }

    private static void requireTrustLevel(TrustDetail trust) {
        if (trust.trustLevel() < MIN_TRUST_LEVEL_TO_OPEN) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    private void requireNotSuspended(long leaderId) {
        if (sanctionQueryService.hasActiveSuspension(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void requireNotWarningSpot(long spotId) {
        if (activeSpotChecker.isWarningBakji(spotId)) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_WARNING_SPOT);
        }
    }

    private static Basecamp open(long leaderId, long spotId, BasecampDetails details, Instant now) {
        try {
            return Basecamp.open(leaderId, spotId, details, now);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }
}
