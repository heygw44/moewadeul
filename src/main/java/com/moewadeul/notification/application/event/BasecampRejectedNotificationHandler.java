package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampRejectedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Decision> {

    BasecampRejectedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_REJECTED,
                NotificationPayloads.Decision.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Decision payload) {
        return NotificationTemplates.rejected(payload);
    }
}
