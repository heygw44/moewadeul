package com.moewadeul.member.infra.verification;

import com.moewadeul.common.outbox.OutboxEventStatus;
import com.moewadeul.member.domain.verification.SendWindow;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmailVerificationMapper {

    int incrementAttempt(@Param("id") long id, @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    int markVerified(@Param("id") long id, @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    SendWindow summarizeQueuedSendsByIpSince(
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("status") OutboxEventStatus status,
            @Param("requestIp") String requestIp,
            @Param("since") Instant since);
}
