package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.application.JoinEligibilityChecker;
import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampApplication;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠프 리더가 대기 중인 합류 신청을 승인하거나 거절하고, 신청자에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
 *
 * <p>마지막 자리를 두고 승인 두 건이 겹치거나 마감과 승인이 겹쳐도 정원을 넘지 않도록, 베이스캠프 행을 쓰기 잠금으로 읽어
 * 같은 베이스캠프의 신청·취소·승인·거절을 한 번에 하나씩 처리한다.
 */
@Service
@RequiredArgsConstructor
public class BasecampApprovalService {

    private final BasecampRepository basecampRepository;
    private final JoinEligibilityChecker joinEligibilityChecker;
    private final ActiveSpotChecker activeSpotChecker;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 leaderId인 캠프 리더가 applicationId인 신청을 승인하고, 승인한 뒤의 인원과 베이스캠프 상태를 돌려준다.
     * 승인해서 정원이 차면 베이스캠프는 자동으로 마감된다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다. 거부하면 신청은 대기 중으로 남는다.
     * <ol>
     *   <li>베이스캠프가 없으면 NOT_FOUND
     *   <li>요청자가 캠프 리더가 아니면 ACCESS_DENIED
     *   <li>베이스캠프의 장소가 숨김·삭제·검토 대기여서 ACTIVE가 아니면 NOT_FOUND
     *   <li>이 베이스캠프의 신청이 아니면 NOT_FOUND
     *   <li>모집 중이 아니거나 신청이 대기 중이 아니면 BASECAMP_INVALID_STATE
     *   <li>정원이 이미 차 있으면 BASECAMP_FULL
     *   <li>신청자가 지금 합류 조건의 최소 신뢰 단계, 연령대, 성별을 충족하지 못하면 BASECAMP_CONDITION_NOT_MET
     *   <li>신청자가 같은 기간에 진행 중인(모집 중·마감·확정) 다른 베이스캠프의 ACTIVE 멤버이면 BASECAMP_DATE_CONFLICT
     * </ol>
     */
    @Transactional
    public BasecampApproveResult approve(long basecampId, long applicationId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        // 장소가 지도에서 내려가면 새 멤버를 받지 않는다. 숨긴 장소가 있다는 사실을 드러내지 않으려고 NOT_FOUND로 거부한다.
        // 거절은 대기 신청을 정리하는 일이라 장소 상태와 상관없이 할 수 있다.
        activeSpotChecker.requireActive(basecamp.getSpotId());
        BasecampApplication application = basecamp.checkApprovable(applicationId);
        long applicantId = application.getApplicantId();
        Applicant applicant = joinEligibilityChecker.applicantOf(applicantId);
        joinEligibilityChecker.requireEligible(basecamp, applicant, applicantId);

        basecamp.approve(applicationId, clock.instant());
        recordDecision(BasecampDecisionEvents.APPROVED_EVENT_TYPE, basecamp, application);
        return new BasecampApproveResult(
                basecamp.headcount(), basecamp.getStatus().name());
    }

    /**
     * 호출하면 leaderId인 캠프 리더가 applicationId인 신청을 거절하고 그 신청의 ID와 상태를 돌려준다. 거절된 회원은 다시 신청할 수 없다.
     *
     * <p>베이스캠프가 없거나 이 베이스캠프의 신청이 아니면 NOT_FOUND, 요청자가 캠프 리더가 아니면 ACCESS_DENIED이다.
     * 모집 중이 아니거나 신청이 대기 중이 아니면 BASECAMP_INVALID_STATE이다.
     */
    @Transactional
    public BasecampRejectResult reject(long basecampId, long applicationId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        BasecampApplication application = basecamp.reject(applicationId, clock.instant());
        recordDecision(BasecampDecisionEvents.REJECTED_EVENT_TYPE, basecamp, application);
        return new BasecampRejectResult(
                application.getId(), application.getStatus().name());
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

    private void recordDecision(String eventType, Basecamp basecamp, BasecampApplication application) {
        outboxEventRecorder.record(
                eventType,
                BasecampDecisionEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampDecisionEvents.Payload(
                        application.getId(), application.getApplicantId(), basecamp.getId()));
    }
}
