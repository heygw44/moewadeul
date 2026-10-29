package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramApplicationConfirmedNotificationHandler
        extends NotificationEventHandler<ProgramEvents.ApplicationPayload> {

    ProgramApplicationConfirmedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                ProgramEvents.APPLICATION_CONFIRMED,
                ProgramEvents.ApplicationPayload.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, ProgramEvents.ApplicationPayload payload) {
        return NotificationTemplates.programApplicationConfirmed(payload);
    }
}
