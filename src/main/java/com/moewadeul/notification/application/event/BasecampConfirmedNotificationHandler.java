package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampConfirmedNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampConfirmedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_CONFIRMED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.confirmed(payload);
    }
}
