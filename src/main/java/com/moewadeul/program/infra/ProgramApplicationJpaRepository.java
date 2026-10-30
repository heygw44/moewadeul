package com.moewadeul.program.infra;

import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramCancelReason;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramApplicationJpaRepository
        extends JpaRepository<ProgramApplication, Long>, ProgramApplicationRepository, ProgramSeatHolderCounter {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ProgramApplication a where a.id = :id")
    Optional<ProgramApplication> findByIdForUpdate(@Param("id") Long id);

    @Override
    @Query("select a.programId from ProgramApplication a where a.id = :id")
    Optional<Long> findProgramId(@Param("id") long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from ProgramApplication a
            where a.programId = :programId
              and a.status in (
                  com.moewadeul.program.domain.ProgramApplicationStatus.PENDING_PAYMENT,
                  com.moewadeul.program.domain.ProgramApplicationStatus.CONFIRMED)
            order by a.id
            """)
    List<ProgramApplication> findActiveByProgramForUpdate(@Param("programId") long programId);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
            update ProgramApplication a
            set a.status = com.moewadeul.program.domain.ProgramApplicationStatus.CANCELED,
                a.cancelReason = :reason,
                a.canceledAt = :now,
                a.updatedAt = :now
            where a.programId = :programId
              and a.status in (
                  com.moewadeul.program.domain.ProgramApplicationStatus.PENDING_PAYMENT,
                  com.moewadeul.program.domain.ProgramApplicationStatus.CONFIRMED)
            """)
    int cancelActiveByProgram(
            @Param("programId") long programId, @Param("reason") ProgramCancelReason reason, @Param("now") Instant now);

    @Override
    @Query("""
            select a.id from ProgramApplication a
            where a.memberId = :memberId
              and a.status = com.moewadeul.program.domain.ProgramApplicationStatus.PENDING_PAYMENT
            order by a.id
            """)
    List<Long> findPendingPaymentIdsByMember(@Param("memberId") long memberId);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
            update ProgramApplication a
            set a.paymentDueAt = :startAt,
                a.updatedAt = :now
            where a.programId = :programId
              and a.status = com.moewadeul.program.domain.ProgramApplicationStatus.PENDING_PAYMENT
              and a.paymentDueAt > :startAt
            """)
    int capPendingPaymentDueAt(
            @Param("programId") long programId, @Param("startAt") Instant startAt, @Param("now") Instant now);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
            update ProgramApplication a
            set a.reviewRequestedAt = :now,
                a.updatedAt = :now
            where a.memberId = :memberId
              and a.status = com.moewadeul.program.domain.ProgramApplicationStatus.CONFIRMED
              and a.reviewRequestedAt is null
            """)
    int requestReviewForConfirmed(@Param("memberId") long memberId, @Param("now") Instant now);

    @Override
    boolean existsByProgramId(long programId);
}
