package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.RemovalCause;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.trust.application.withdrawal.WithdrawalEvents;
import org.springframework.stereotype.Component;

@Component
class WithdrawalBasecampCleanupHandler implements OutboxEventHandler {

    private final BasecampSanctionCleanupService cleanupService;

    WithdrawalBasecampCleanupHandler(BasecampSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return WithdrawalEvents.BASECAMP_CLEANUP_EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), RemovalCause.WITHDRAWAL);
    }
}
