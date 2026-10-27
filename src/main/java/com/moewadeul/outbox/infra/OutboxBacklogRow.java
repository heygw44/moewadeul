package com.moewadeul.outbox.infra;

import java.time.Instant;

public record OutboxBacklogRow(long pendingCount, long failedCount, Instant oldestPendingCreatedAt) {}
