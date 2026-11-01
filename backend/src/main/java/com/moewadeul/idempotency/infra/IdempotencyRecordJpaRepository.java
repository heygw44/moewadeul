package com.moewadeul.idempotency.infra;

import com.moewadeul.idempotency.domain.IdempotencyRecord;
import com.moewadeul.idempotency.domain.IdempotencyRecordId;
import com.moewadeul.idempotency.domain.IdempotencyRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId>, IdempotencyRecordRepository {}
