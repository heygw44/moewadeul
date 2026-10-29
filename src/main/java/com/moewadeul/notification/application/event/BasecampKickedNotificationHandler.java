package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampKickedNotificationHandler extends NotificationEventHandler<BasecampEvents.KickedPayload> {

    BasecampKickedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(BasecampEvents.KICKED, BasecampEvents.KickedPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, BasecampEvents.KickedPayload payload) {
        return NotificationTemplates.kicked(payload);
    }
}
