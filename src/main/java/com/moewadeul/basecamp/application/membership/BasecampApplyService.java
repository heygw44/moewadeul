package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.application.JoinEligibilityChecker;
import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampApplication;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
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
 * 본인확인을 마친 회원이 모집 중인 베이스캠프에 합류를 신청하고, 캠프 리더에게 알릴 이벤트를 같은 트랜잭션에서 기록한다.
 *
 * <p>대기 신청은 20건까지만 받는다. 동시에 들어온 신청이 모두 20건 미만으로 읽고 통과하지 못하도록, 베이스캠프 행을 쓰기 잠금으로 읽어
 * 같은 베이스캠프의 신청과 취소를 한 번에 하나씩 처리한다.
 */
@Service
@RequiredArgsConstructor
public class BasecampApplyService {

    private final BasecampRepository basecampRepository;
    private final JoinEligibilityChecker joinEligibilityChecker;
    private final ActiveSpotChecker activeSpotChecker;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    /**
     * 호출하면 command.memberId인 회원의 합류 신청을 만들고 그 ID와 상태를 돌려준다. 스스로 취소했던 신청은 같은 신청 ID로 다시 대기가 된다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>베이스캠프가 없거나, 베이스캠프의 장소가 숨김·삭제·검토 대기여서 ACTIVE가 아니면 NOT_FOUND
     *   <li>신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT
     *   <li>모집 중이 아니면 BASECAMP_INVALID_STATE, 이미 신청했거나 멤버이면 BASECAMP_ALREADY_APPLIED,
     *       거절·탈퇴·강퇴된 적이 있으면 BASECAMP_REAPPLY_NOT_ALLOWED
     *   <li>합류 조건의 최소 신뢰 단계, 연령대, 성별을 충족하지 못하면 BASECAMP_CONDITION_NOT_MET
     *   <li>같은 기간에 진행 중인(모집 중·마감·확정) 다른 베이스캠프의 ACTIVE 멤버이면 BASECAMP_DATE_CONFLICT
     *   <li>대기 신청이 이미 20건이면 BASECAMP_PENDING_LIMIT
     *   <li>메시지가 500자를 넘으면 INVALID_INPUT
     * </ol>
     */
    @Transactional
    public BasecampApplyResult apply(BasecampApplyCommand command) {
        // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
        // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 신청이 보이지 않는다.
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(command.basecampId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // 장소 상태는 잠금을 얻은 뒤에 읽는다. 숨긴 장소가 있다는 사실을 드러내지 않으려고 없는 베이스캠프와 같은 NOT_FOUND로 거부한다.
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
                BasecampApplicationEvents.EVENT_TYPE,
                BasecampApplicationEvents.AGGREGATE_TYPE,
                basecamp.getId(),
                new BasecampApplicationEvents.Payload(application.getId(), command.memberId(), basecamp.getLeaderId()));
        return new BasecampApplyResult(
                application.getId(), application.getStatus().name());
    }

    private static void requireBaseTrustLevel(Applicant applicant) {
        if (applicant.trustLevel() < JoinEligibilityPolicy.BASE_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    // 메시지 길이처럼 도메인이 IllegalArgumentException으로 알리는 요청 값 오류는 INVALID_INPUT으로 바꾼다.
    private BasecampApplication submit(Basecamp basecamp, BasecampApplyCommand command) {
        try {
            return basecamp.apply(command.memberId(), command.message(), clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }
}
