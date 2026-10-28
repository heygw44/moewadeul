package com.moewadeul.notification.application.inbox;

import java.util.List;

public record NotificationPage(List<NotificationItem> content, int page, int size, boolean hasNext) {}
