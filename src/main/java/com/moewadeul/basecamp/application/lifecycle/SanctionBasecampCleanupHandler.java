package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.RemovalCause;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

@Component
class SanctionBasecampCleanupHandler implements OutboxEventHandler {

    static final String EVENT_TYPE = "SANCTION_BASECAMP_CLEANUP";

    private final BasecampSanctionCleanupService cleanupService;

    SanctionBasecampCleanupHandler(BasecampSanctionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId(), RemovalCause.SANCTION);
    }
}
