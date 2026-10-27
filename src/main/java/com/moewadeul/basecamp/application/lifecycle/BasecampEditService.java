package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.BasecampRevision;
import com.moewadeul.basecamp.domain.Capacity;
import com.moewadeul.basecamp.domain.JoinCondition;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampEditService {

    private final BasecampRepository basecampRepository;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    @Transactional
    public BasecampStatusResult registerContact(long basecampId, long leaderId, String contactInfo) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        try {
            basecamp.registerContact(contactInfo, clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
        return resultOf(basecamp);
    }

    @Transactional
    public BasecampStatusResult revise(long basecampId, long leaderId, BasecampReviseCommand command) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        BasecampRevision revision = toRevision(basecamp, command, leaderId);
        try {
            basecamp.revise(revision, clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
        return resultOf(basecamp);
    }

    private BasecampRevision toRevision(Basecamp basecamp, BasecampReviseCommand command, long leaderId) {
        Capacity capacity = command.capacity() == null ? basecamp.getCapacity() : Capacity.of(command.capacity());
        JoinCondition joinCondition = command.joinCondition() == null
                ? basecamp.getJoinCondition()
                : JoinConditions.from(command.joinCondition(), trustSummaryService.detail(leaderId));
        return new BasecampRevision(
                command.title() == null ? basecamp.getTitle() : command.title(),
                command.description() == null ? basecamp.getDescription() : command.description(),
                capacity,
                joinCondition);
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

    private static BasecampStatusResult resultOf(Basecamp basecamp) {
        return new BasecampStatusResult(basecamp.getId(), basecamp.getStatus().name());
    }
}
