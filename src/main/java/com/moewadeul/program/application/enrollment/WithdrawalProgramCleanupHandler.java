package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.program.domain.ProgramCancelReason;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class WithdrawalProgramCleanupHandler implements OutboxEventHandler {

    private final ProgramSanctionCleanupService cleanupService;
    private final ProgramVacancyAlertService vacancyAlertService;

    WithdrawalProgramCleanupHandler(
            ProgramSanctionCleanupService cleanupService, ProgramVacancyAlertService vacancyAlertService) {
        this.cleanupService = cleanupService;
        this.vacancyAlertService = vacancyAlertService;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(TrustEvents.WITHDRAWAL_PROGRAM_CLEANUP);
    }

    @Override
    public void handle(OutboxMessage message) {
        vacancyAlertService.unsubscribeAll(message.aggregateId());
        cleanupService.cleanUp(message.aggregateId(), ProgramCancelReason.WITHDRAWN);
    }
}
