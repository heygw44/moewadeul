package com.moewadeul.notification.domain;

import java.util.List;

public record NotificationSlice(List<Notification> content, boolean hasNext) {}
