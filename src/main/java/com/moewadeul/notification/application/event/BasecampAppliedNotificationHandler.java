package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampAppliedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Applied> {

    BasecampAppliedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_APPLIED,
                NotificationPayloads.Applied.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Applied payload) {
        return NotificationTemplates.applied(message.aggregateId(), payload);
    }
}
