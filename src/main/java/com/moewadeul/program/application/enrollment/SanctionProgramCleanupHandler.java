package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.trust.application.sanction.SanctionEvents;
import org.springframework.stereotype.Component;

@Component
class SanctionProgramCleanupHandler implements OutboxEventHandler {

    private final ProgramSanctionCleanupService cleanupService;

    SanctionProgramCleanupHandler(ProgramSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return SanctionEvents.PROGRAM_CLEANUP_EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), ProgramCancelReason.SANCTIONED);
    }
}
