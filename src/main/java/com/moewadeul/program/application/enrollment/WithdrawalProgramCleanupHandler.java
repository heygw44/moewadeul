package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.trust.application.withdrawal.WithdrawalEvents;
import org.springframework.stereotype.Component;

@Component
class WithdrawalProgramCleanupHandler implements OutboxEventHandler {

    private final ProgramSanctionCleanupService cleanupService;

    WithdrawalProgramCleanupHandler(ProgramSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return WithdrawalEvents.PROGRAM_CLEANUP_EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), ProgramCancelReason.WITHDRAWN);
    }
}
