package com.moewadeul.memory.application;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

@Component
class WithdrawalMemoryCleanupHandler implements OutboxEventHandler {

    static final String EVENT_TYPE = "WITHDRAWAL_MEMORY_CLEANUP";

    private final MemoryWithdrawalCleanupService cleanupService;

    WithdrawalMemoryCleanupHandler(MemoryWithdrawalCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId());
    }
}
