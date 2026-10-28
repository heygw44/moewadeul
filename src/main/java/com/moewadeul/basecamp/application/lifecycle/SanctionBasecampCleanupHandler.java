package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.RemovalCause;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.trust.application.sanction.SanctionEvents;
import org.springframework.stereotype.Component;

@Component
class SanctionBasecampCleanupHandler implements OutboxEventHandler {

    private final BasecampSanctionCleanupService cleanupService;

    SanctionBasecampCleanupHandler(BasecampSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), RemovalCause.SANCTION);
    }
}
