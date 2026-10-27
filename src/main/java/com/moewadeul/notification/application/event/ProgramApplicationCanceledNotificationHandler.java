package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramApplicationCanceledNotificationHandler
        extends NotificationEventHandler<NotificationPayloads.ProgramApplicationCanceled> {

    ProgramApplicationCanceledNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.PROGRAM_APPLICATION_CANCELED,
                NotificationPayloads.ProgramApplicationCanceled.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ProgramApplicationCanceled payload) {
        return NotificationTemplates.programApplicationCanceled(payload);
    }
}
