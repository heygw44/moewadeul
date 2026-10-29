package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class MemberReportResolvedNotificationHandler extends NotificationEventHandler<TrustEvents.ReportResolvedPayload> {

    MemberReportResolvedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(TrustEvents.REPORT_RESOLVED, TrustEvents.ReportResolvedPayload.class, jsonMapper, notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, TrustEvents.ReportResolvedPayload payload) {
        return NotificationTemplates.reportResolved(payload);
    }
}
