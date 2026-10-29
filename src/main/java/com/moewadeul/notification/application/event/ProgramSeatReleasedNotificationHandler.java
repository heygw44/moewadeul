package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramSeatReleasedNotificationHandler extends NotificationEventHandler<ProgramEvents.SeatReleasedPayload> {

    ProgramSeatReleasedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(ProgramEvents.SEAT_RELEASED, ProgramEvents.SeatReleasedPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, ProgramEvents.SeatReleasedPayload payload) {
        return NotificationTemplates.programSeatReleased(payload);
    }
}
