package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampAppliedNotificationHandler extends NotificationEventHandler<BasecampEvents.AppliedPayload> {

    BasecampAppliedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(BasecampEvents.APPLIED, BasecampEvents.AppliedPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, BasecampEvents.AppliedPayload payload) {
        return NotificationTemplates.applied(message.aggregateId(), payload);
    }
}
