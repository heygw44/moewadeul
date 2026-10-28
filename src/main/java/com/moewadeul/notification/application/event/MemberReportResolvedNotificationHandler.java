package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class MemberReportResolvedNotificationHandler extends NotificationEventHandler<NotificationPayloads.ReportResolved> {

    MemberReportResolvedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.MEMBER_REPORT_RESOLVED,
                NotificationPayloads.ReportResolved.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ReportResolved payload) {
        return NotificationTemplates.reportResolved(payload);
    }
}
