package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampApprovedNotificationHandler extends NotificationEventHandler<BasecampEvents.DecisionPayload> {

    BasecampApprovedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(BasecampEvents.APPROVED, BasecampEvents.DecisionPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, BasecampEvents.DecisionPayload payload) {
        return NotificationTemplates.approved(payload);
    }
}
