package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.program.domain.ProgramCancelReason;
import org.springframework.stereotype.Component;

@Component
class WithdrawalProgramCleanupHandler implements OutboxEventHandler {

    static final String EVENT_TYPE = "WITHDRAWAL_PROGRAM_CLEANUP";

    private final ProgramSanctionCleanupService cleanupService;

    WithdrawalProgramCleanupHandler(ProgramSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), ProgramCancelReason.WITHDRAWN);
    }
}
