package com.moewadeul.publicdata.application;

import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Duration;

public interface SyncJob<R> {

    SyncJobType type();

    Duration staleAfter();

    R run(SyncJobStart start);
}
