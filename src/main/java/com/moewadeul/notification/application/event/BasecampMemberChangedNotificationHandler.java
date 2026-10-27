package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BasecampMemberChangedNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampMemberChangedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_MEMBER_CHANGED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.memberChanged(payload);
    }
}
