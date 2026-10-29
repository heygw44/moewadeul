package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.application.JoinEligibilityChecker;
import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampApplication;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampApplyService {

    private final BasecampRepository basecampRepository;
    private final JoinEligibilityChecker joinEligibilityChecker;
    private final ActiveSpotChecker activeSpotChecker;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public BasecampApplyResult apply(BasecampApplyCommand command) {
        // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
        // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 신청이 보이지 않는다.
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(command.basecampId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        activeSpotChecker.requireActive(basecamp.getSpotId());
        Applicant applicant = joinEligibilityChecker.applicantOf(command.memberId());
        requireBaseTrustLevel(applicant);
        basecamp.checkApplicable(command.memberId());
        joinEligibilityChecker.requireEligible(basecamp, applicant, command.memberId());

        BasecampApplication application = submit(basecamp, command);
        // 베이스캠프는 이미 영속 상태라 flush만 하면 새 신청이 저장되고 ID가 채워진다.
        // saveAndFlush를 쓰면 JPA가 병합하면서 새 신청을 복사본으로 저장해서, 돌려받은 객체에 ID가 없다.
        basecampRepository.flush();
        outboxEventRecorder.record(
                BasecampEvents.APPLIED,
                BasecampEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampEvents.AppliedPayload(application.getId(), command.memberId(), basecamp.getLeaderId()));
        return new BasecampApplyResult(application.getId(), application.getStatus());
    }

    private static void requireBaseTrustLevel(Applicant applicant) {
        if (applicant.trustLevel() < JoinEligibilityPolicy.BASE_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    private BasecampApplication submit(Basecamp basecamp, BasecampApplyCommand command) {
        try {
            return basecamp.apply(command.memberId(), command.message(), clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }
}
