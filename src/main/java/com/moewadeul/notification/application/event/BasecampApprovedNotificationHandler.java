package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampApprovedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Decision> {

    BasecampApprovedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_APPROVED,
                NotificationPayloads.Decision.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Decision payload) {
        return NotificationTemplates.approved(payload);
    }
}
