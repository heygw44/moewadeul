package com.moewadeul.outbox.infra;

import com.moewadeul.common.outbox.OutboxLane;
import java.time.Instant;

public record OutboxBacklogRow(OutboxLane lane, long pendingCount, long failedCount, Instant oldestPendingCreatedAt) {}
