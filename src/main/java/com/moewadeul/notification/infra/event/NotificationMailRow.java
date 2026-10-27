package com.moewadeul.notification.infra.event;

public record NotificationMailRow(
        long notificationId, String email, String title, String body, String link, boolean deliverable) {

    @Override
    public String toString() {
        return "NotificationMailRow[REDACTED]";
    }
}
