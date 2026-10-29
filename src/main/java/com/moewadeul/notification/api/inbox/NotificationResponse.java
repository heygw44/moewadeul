package com.moewadeul.notification.api.inbox;

import com.moewadeul.notification.application.inbox.NotificationItem;
import java.time.Instant;

public record NotificationResponse(
        long notificationId, String type, String title, String body, String link, Instant readAt, Instant createdAt) {

    static NotificationResponse from(NotificationItem item) {
        return new NotificationResponse(
                item.notificationId(),
                item.type(),
                item.title(),
                item.body(),
                item.link(),
                item.readAt(),
                item.createdAt());
    }
}
