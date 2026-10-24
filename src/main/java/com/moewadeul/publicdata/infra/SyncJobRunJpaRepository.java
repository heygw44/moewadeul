package com.moewadeul.publicdata.infra;

import com.moewadeul.publicdata.domain.SyncJobRun;
import com.moewadeul.publicdata.domain.SyncJobRunRepository;
import com.moewadeul.publicdata.domain.SyncJobStatus;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
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
