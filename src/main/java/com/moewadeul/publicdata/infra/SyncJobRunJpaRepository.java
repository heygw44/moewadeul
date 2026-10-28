package com.moewadeul.publicdata.infra;

import com.moewadeul.publicdata.domain.SyncJobRun;
import com.moewadeul.publicdata.domain.SyncJobRunRepository;
import com.moewadeul.publicdata.domain.SyncJobStatus;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncJobRunJpaRepository extends JpaRepository<SyncJobRun, Long>, SyncJobRunRepository {

    @Override
    SyncJobRun save(SyncJobRun run);

    @Override
    Optional<SyncJobRun> findById(Long id);

    @Override
    Optional<SyncJobRun> findFirstByJobTypeOrderByIdDesc(SyncJobType jobType);

    @Override
    boolean existsByJobTypeAndStatusAndFinishedAtAfter(SyncJobType jobType, SyncJobStatus status, Instant after);

    @Override
    void flush();
}
