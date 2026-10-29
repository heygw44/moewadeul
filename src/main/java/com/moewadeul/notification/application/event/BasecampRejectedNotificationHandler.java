package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampRejectedNotificationHandler extends NotificationEventHandler<BasecampEvents.DecisionPayload> {

    BasecampRejectedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(BasecampEvents.REJECTED, BasecampEvents.DecisionPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, BasecampEvents.DecisionPayload payload) {
        return NotificationTemplates.rejected(payload);
    }
}
