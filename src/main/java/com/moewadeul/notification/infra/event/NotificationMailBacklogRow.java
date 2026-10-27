package com.moewadeul.notification.infra.event;

import java.time.Instant;

public record NotificationMailBacklogRow(long pendingCount, long failedCount, Instant oldestPendingCreatedAt) {}
