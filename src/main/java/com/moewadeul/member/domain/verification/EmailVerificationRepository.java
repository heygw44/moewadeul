package com.moewadeul.member.domain.verification;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification emailVerification);

    /** 호출하면 그 발송 요청 이벤트로 만든 코드 행을 돌려준다. 아직 처리하지 않은 이벤트이면 비어 있다. */
    Optional<EmailVerification> findByOutboxEventId(long outboxEventId);

    /** 호출하면 그 회원에게 가장 나중에 발급한 코드 행을 돌려준다. 인증을 마친 행도 포함한다. */
    Optional<EmailVerification> findLatestByMemberId(long memberId);

    /** 호출하면 그 회원에게 가장 나중에 코드를 발급한 시각을 돌려준다. */
    Optional<Instant> findLastSentAt(long memberId);

    /** 호출하면 {@code since}보다 나중에(같은 시각은 제외) 그 회원에게 발급한 코드의 수와 가장 오래된 발급 시각을 돌려준다. */
    SendWindow summarizeSentToMemberSince(long memberId, Instant since);

    /** 호출하면 {@code since}보다 나중에(같은 시각은 제외) 그 IP가 요청해서 발급한 코드 행의 수와 가장 오래된 발급 시각을 돌려준다. */
    SendWindow summarizeSentByIpSince(String requestIp, Instant since);
}
