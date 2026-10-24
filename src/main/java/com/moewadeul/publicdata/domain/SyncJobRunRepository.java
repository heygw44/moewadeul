package com.moewadeul.publicdata.domain;

import java.time.Instant;
import java.util.Optional;

public interface SyncJobRunRepository {

    SyncJobRun save(SyncJobRun run);

    Optional<SyncJobRun> findById(Long id);

    /**
     * 호출하면 그 작업 종류의 가장 최근 실행 기록을 잠그지 않고 읽는다. 같은 종류를 동시에 시작하는 호출은 DB의 유니크 제약이 하나만 받는다.
     */
    Optional<SyncJobRun> findFirstByJobTypeOrderByIdDesc(SyncJobType jobType);

    /** 호출하면 jobType 실행 가운데 status이고 finished_at이 after보다 늦은 기록이 있는지 돌려준다. */
    boolean existsByJobTypeAndStatusAndFinishedAtAfter(SyncJobType jobType, SyncJobStatus status, Instant after);

    /** 호출하면 이 트랜잭션에서 바꾼 실행 기록을 바로 DB에 쓴다. */
    void flush();
}
