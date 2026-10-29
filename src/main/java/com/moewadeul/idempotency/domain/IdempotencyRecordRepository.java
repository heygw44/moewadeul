package com.moewadeul.idempotency.domain;

import java.util.Optional;

public interface IdempotencyRecordRepository {

    <S extends IdempotencyRecord> S saveAndFlush(S record);

    <S extends IdempotencyRecord> S save(S record);

    Optional<IdempotencyRecord> findById(IdempotencyRecordId id);

    void deleteById(IdempotencyRecordId id);
}
