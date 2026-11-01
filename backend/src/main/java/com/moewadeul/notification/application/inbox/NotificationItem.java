package com.moewadeul.notification.application.inbox;

import java.time.Instant;

public record NotificationItem(
        long notificationId, String type, String title, String body, String link, Instant readAt, Instant createdAt) {}
