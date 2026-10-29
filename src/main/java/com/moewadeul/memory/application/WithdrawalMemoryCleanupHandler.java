package com.moewadeul.memory.application;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

@Component
class WithdrawalMemoryCleanupHandler implements OutboxEventHandler {

    private final MemoryWithdrawalCleanupService cleanupService;

    WithdrawalMemoryCleanupHandler(MemoryWithdrawalCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return TrustEvents.WITHDRAWAL_MEMORY_CLEANUP;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId());
    }
}
