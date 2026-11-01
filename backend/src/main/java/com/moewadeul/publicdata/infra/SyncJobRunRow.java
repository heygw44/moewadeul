package com.moewadeul.publicdata.infra;

import com.moewadeul.publicdata.domain.SyncJobStatus;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Instant;

public record SyncJobRunRow(
        long id,
        SyncJobType jobType,
        SyncJobStatus status,
        int processedCount,
        int skippedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {}
