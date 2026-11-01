package com.moewadeul.member.domain.verification;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification emailVerification);

    Optional<EmailVerification> findByOutboxEventId(long outboxEventId);

    Optional<EmailVerification> findLatestByMemberId(long memberId);

    Optional<Instant> findLastSentAt(long memberId);

    SendWindow summarizeSentToMemberSince(long memberId, Instant since);

    SendWindow summarizeSentByIpSince(String requestIp, Instant since);
}
