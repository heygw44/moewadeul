package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.application.JoinEligibilityChecker;
import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampApplication;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.basecamp.infra.BasecampLockMapper;
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
public class BasecampApprovalService {

    private final BasecampRepository basecampRepository;
    private final BasecampLockMapper basecampLockMapper;
    private final JoinEligibilityChecker joinEligibilityChecker;
    private final ActiveSpotChecker activeSpotChecker;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public BasecampApproveResult approve(long basecampId, long applicationId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        lockApplicant(basecampId, applicationId);
        activeSpotChecker.requireActive(basecamp.getSpotId());
        BasecampApplication application = basecamp.checkApprovable(applicationId);
        long applicantId = application.getApplicantId();
        Applicant applicant = joinEligibilityChecker.applicantOf(applicantId);
        joinEligibilityChecker.requireEligible(basecamp, applicant, applicantId);

        basecamp.approve(applicationId, clock.instant());
        recordDecision(BasecampEvents.APPROVED, basecamp, application);
        return new BasecampApproveResult(basecamp.headcount(), basecamp.getStatus());
    }

    @Transactional
    public BasecampRejectResult reject(long basecampId, long applicationId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        BasecampApplication application = basecamp.reject(applicationId, clock.instant());
        recordDecision(BasecampEvents.REJECTED, basecamp, application);
        return new BasecampRejectResult(application.getId(), application.getStatus());
    }

    // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
    // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 변경이 보이지 않는다.
    private Basecamp lockLedBy(long basecampId, long leaderId) {
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return basecamp;
    }

    // 기간이 겹치는 다른 베이스캠프의 멤버인지 보는 조회는 잠그지 않는 읽기다. 그래서 신청자의 회원 행 잠금도 이 트랜잭션의 어떤 잠그지 않는
    // 읽기보다 먼저 와야 한다. 그래야 겹치는 조회가 잠금을 얻은 뒤의 스냅숏을 읽어, 먼저 끝난 다른 승인이 넣은 멤버 행이 보인다.
    private void lockApplicant(long basecampId, long applicationId) {
        Long applicantId = basecampLockMapper.lockApplicantId(basecampId, applicationId);
        if (applicantId != null) {
            basecampLockMapper.lockMember(applicantId);
        }
    }

    private void recordDecision(String eventType, Basecamp basecamp, BasecampApplication application) {
        outboxEventRecorder.record(
                eventType,
                BasecampEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampEvents.DecisionPayload(
                        application.getId(), application.getApplicantId(), basecamp.getId()));
    }
}
