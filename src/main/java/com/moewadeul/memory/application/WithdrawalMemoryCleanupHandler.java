package com.moewadeul.memory.application;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class WithdrawalMemoryCleanupHandler implements OutboxEventHandler {

    private final MemoryWithdrawalCleanupService cleanupService;

    WithdrawalMemoryCleanupHandler(MemoryWithdrawalCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(TrustEvents.WITHDRAWAL_MEMORY_CLEANUP);
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId());
    }
}
