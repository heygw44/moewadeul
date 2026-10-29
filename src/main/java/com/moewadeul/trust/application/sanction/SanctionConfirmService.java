package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.MemberSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionConfirmService {

    private final SanctionConfirmApplier sanctionConfirmApplier;
    private final MemberSessions memberSessions;

    public SanctionConfirmResult confirm(SanctionConfirmCommand command) {
        OutsideTransaction.require("제재·신고 처리가 커밋된 뒤에 세션을 지워야 하고, 세션 삭제는 트랜잭션 안에서 부르면 커넥션을 하나 더 잡는다.");
        SanctionConfirmResult result = sanctionConfirmApplier.apply(command);
        if (result.suspended()) {
            memberSessions.invalidateAll(result.memberId());
        }
        log.info(
                "sanction confirmed memberId={} sanctionId={} type={}",
                result.memberId(),
                result.sanctionId(),
                command.type());
        return result;
    }
}
