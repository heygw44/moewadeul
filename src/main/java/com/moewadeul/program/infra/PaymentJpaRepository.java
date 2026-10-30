package com.moewadeul.program.infra;

import com.moewadeul.program.domain.Payment;
import com.moewadeul.program.domain.PaymentRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long>, PaymentRepository {

    @Override
    Payment saveAndFlush(Payment payment);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.programApplicationId = :programApplicationId")
    Optional<Payment> findByProgramApplicationIdForUpdate(@Param("programApplicationId") long programApplicationId);

    @Override
    @Query("""
            select p.programApplicationId from Payment p
            where p.status = com.moewadeul.program.domain.PaymentStatus.PAID
              and p.programApplicationId in (
                  select a.id from ProgramApplication a where a.programId = :programId)
            """)
    List<Long> findPaidApplicationIdsByProgram(@Param("programId") long programId);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
            update Payment p
            set p.status = com.moewadeul.program.domain.PaymentStatus.REFUNDED,
                p.refundedAt = :now,
                p.updatedAt = :now
            where p.status = com.moewadeul.program.domain.PaymentStatus.PAID
              and p.programApplicationId in (
                  select a.id from ProgramApplication a where a.programId = :programId)
            """)
    int refundPaidByProgram(@Param("programId") long programId, @Param("now") Instant now);
}
