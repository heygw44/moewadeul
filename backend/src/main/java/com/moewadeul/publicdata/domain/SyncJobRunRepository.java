package com.moewadeul.publicdata.domain;

import java.time.Instant;
import java.util.Optional;

public interface SyncJobRunRepository {

    SyncJobRun save(SyncJobRun run);

    Optional<SyncJobRun> findById(Long id);

    Optional<SyncJobRun> findFirstByJobTypeOrderByIdDesc(SyncJobType jobType);

    boolean existsByJobTypeAndStatusAndFinishedAtAfter(SyncJobType jobType, SyncJobStatus status, Instant after);

    void flush();
}
