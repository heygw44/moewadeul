package com.moewadeul.member.infra.verification;

import com.moewadeul.member.domain.verification.EmailVerification;
import com.moewadeul.member.domain.verification.EmailVerificationRepository;
import com.moewadeul.member.domain.verification.SendWindow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationJpaRepository
        extends JpaRepository<EmailVerification, Long>, EmailVerificationRepository {

    @Override
    EmailVerification save(EmailVerification emailVerification);

    Optional<EmailVerification> findFirstByMemberIdOrderByIdDesc(long memberId);

    @Override
    Optional<EmailVerification> findByOutboxEventId(long outboxEventId);

    @Override
    default Optional<EmailVerification> findLatestByMemberId(long memberId) {
        return findFirstByMemberIdOrderByIdDesc(memberId);
    }

    @Override
    @Query("SELECT MAX(e.createdAt) FROM EmailVerification e WHERE e.memberId = :memberId")
    Optional<Instant> findLastSentAt(@Param("memberId") long memberId);

    @Override
    @Query("""
            SELECT new com.moewadeul.member.domain.verification.SendWindow(COUNT(e), MIN(e.createdAt))
            FROM EmailVerification e
            WHERE e.memberId = :memberId AND e.createdAt > :since
            """)
    SendWindow summarizeSentToMemberSince(@Param("memberId") long memberId, @Param("since") Instant since);

    @Override
    @Query("""
            SELECT new com.moewadeul.member.domain.verification.SendWindow(COUNT(e), MIN(e.createdAt))
            FROM EmailVerification e
            WHERE e.requestIp = :requestIp AND e.createdAt > :since
            """)
    SendWindow summarizeSentByIpSince(@Param("requestIp") String requestIp, @Param("since") Instant since);
}
