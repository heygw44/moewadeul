package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProgramSeatReleasedNotificationHandler
        extends NotificationEventHandler<NotificationPayloads.ProgramSeatReleased> {

    ProgramSeatReleasedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.PROGRAM_SEAT_RELEASED,
                NotificationPayloads.ProgramSeatReleased.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ProgramSeatReleased payload) {
        return NotificationTemplates.programSeatReleased(payload);
    }
}
