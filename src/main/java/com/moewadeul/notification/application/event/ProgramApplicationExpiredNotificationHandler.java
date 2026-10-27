package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramApplicationExpiredNotificationHandler
        extends NotificationEventHandler<NotificationPayloads.ProgramApplicationExpired> {

    ProgramApplicationExpiredNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.PROGRAM_APPLICATION_EXPIRED,
                NotificationPayloads.ProgramApplicationExpired.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ProgramApplicationExpired payload) {
        return NotificationTemplates.programApplicationExpired(payload);
    }
}
