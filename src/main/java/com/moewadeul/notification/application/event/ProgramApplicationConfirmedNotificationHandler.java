package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramApplicationConfirmedNotificationHandler
        extends NotificationEventHandler<NotificationPayloads.ProgramApplicationConfirmed> {

    ProgramApplicationConfirmedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.PROGRAM_APPLICATION_CONFIRMED,
                NotificationPayloads.ProgramApplicationConfirmed.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ProgramApplicationConfirmed payload) {
        return NotificationTemplates.programApplicationConfirmed(payload);
    }
}
