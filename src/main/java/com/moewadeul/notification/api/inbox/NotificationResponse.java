package com.moewadeul.notification.api.inbox;

import com.moewadeul.notification.application.inbox.NotificationItem;
import com.moewadeul.notification.application.inbox.NotificationPage;
import java.time.Instant;
import java.util.List;

public record NotificationPageResponse(List<Entry> content, int page, int size, boolean hasNext) {

    static NotificationPageResponse from(NotificationPage page) {
        List<Entry> content = page.content().stream().map(Entry::from).toList();
        return new NotificationPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record Entry(
            long notificationId,
            String type,
            String title,
            String body,
            String link,
            Instant readAt,
            Instant createdAt) {

        static Entry from(NotificationItem item) {
            return new Entry(
                    item.notificationId(),
                    item.type(),
                    item.title(),
                    item.body(),
                    item.link(),
                    item.readAt(),
                    item.createdAt());
        }
    }
}
