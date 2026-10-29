package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramApplicationCanceledNotificationHandler extends NotificationEventHandler<ProgramEvents.CanceledPayload> {

    ProgramApplicationCanceledNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(ProgramEvents.APPLICATION_CANCELED, ProgramEvents.CanceledPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, ProgramEvents.CanceledPayload payload) {
        return NotificationTemplates.programApplicationCanceled(payload);
    }
}
