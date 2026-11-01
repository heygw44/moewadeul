package com.moewadeul.notification.application.event;

record NotificationDraft(long memberId, String type, String title, String body, String link) {}
