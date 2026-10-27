package com.moewadeul.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {}
