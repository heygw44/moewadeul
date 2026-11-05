package com.moewadeul.idempotency.infra;

import com.moewadeul.idempotency.domain.IdempotencyRecord;
import com.moewadeul.idempotency.domain.IdempotencyRecordId;
import com.moewadeul.idempotency.domain.IdempotencyRecordRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId>, IdempotencyRecordRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT r FROM IdempotencyRecord r WHERE r.memberId = :#{#id.memberId()} AND r.idempotencyKey = :#{#id.idempotencyKey()}")
    Optional<IdempotencyRecord> findByIdForUpdate(@Param("id") IdempotencyRecordId id);

    @Override
    @Modifying
    @Query("""
            UPDATE IdempotencyRecord r SET r.createdAt = :now
            WHERE r.memberId = :memberId AND r.idempotencyKey = :idempotencyKey
              AND r.createdAt = :claimedAt AND r.responseStatus IS NULL""")
    int takeOverClaim(
            @Param("memberId") long memberId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("claimedAt") Instant claimedAt,
            @Param("now") Instant now);

    @Override
    @Modifying
    @Query("""
            DELETE FROM IdempotencyRecord r
            WHERE r.memberId = :memberId AND r.idempotencyKey = :idempotencyKey
              AND r.createdAt = :claimedAt AND r.responseStatus IS NULL""")
    int releaseClaim(
            @Param("memberId") long memberId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("claimedAt") Instant claimedAt);
}
