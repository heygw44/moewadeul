package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampConfirmedNotificationHandler extends NotificationEventHandler<BasecampEvents.MembersPayload> {

    BasecampConfirmedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(BasecampEvents.CONFIRMED, BasecampEvents.MembersPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, BasecampEvents.MembersPayload payload) {
        return NotificationTemplates.confirmed(payload);
    }
}
