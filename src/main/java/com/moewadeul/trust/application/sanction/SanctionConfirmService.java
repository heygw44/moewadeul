package com.moewadeul.trust.application.sanction;

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
