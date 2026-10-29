package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class SanctionConfirmedNotificationHandler extends NotificationEventHandler<TrustEvents.SanctionConfirmedPayload> {

    SanctionConfirmedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                TrustEvents.SANCTION_CONFIRMED,
                TrustEvents.SanctionConfirmedPayload.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, TrustEvents.SanctionConfirmedPayload payload) {
        return NotificationTemplates.sanctionConfirmed(payload);
    }
}
