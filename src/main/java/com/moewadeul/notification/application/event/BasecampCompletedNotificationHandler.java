package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampCompletedNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampCompletedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_COMPLETED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.completed(payload);
    }
}
