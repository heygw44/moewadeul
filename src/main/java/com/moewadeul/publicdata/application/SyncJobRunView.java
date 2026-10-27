package com.moewadeul.publicdata.application;

import com.moewadeul.publicdata.domain.SyncJobStatus;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Instant;

public record SyncJobRunView(
        long jobRunId,
        SyncJobType jobType,
        SyncJobStatus status,
        int processedCount,
        int skippedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {}
