package com.moewadeul.member.application.verification;

import static com.moewadeul.member.application.MemberGuards.requireRequestIp;

import com.moewadeul.common.outbox.OutboxEventStatus;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.domain.verification.EmailVerificationRepository;
import com.moewadeul.member.domain.verification.SendWindow;
import com.moewadeul.member.infra.verification.EmailVerificationIpLockMapper;
import com.moewadeul.member.infra.verification.EmailVerificationMapper;
import com.moewadeul.member.infra.verification.SignupDuplicateAttemptMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmailSendIpLimiter {

    private final EmailVerificationIpLockMapper ipLockMapper;
    private final SignupDuplicateAttemptMapper signupDuplicateAttemptMapper;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationMapper emailVerificationMapper;
    private final Clock clock;

    /**
     * 호출하는 쪽의 트랜잭션에서 잠그지 않는 조회를 하기 전에 부른다.
     * MySQL은 트랜잭션의 첫 일반 조회 때 읽기 스냅숏을 정한다. 그래서 먼저 조회하면, 잠금을 기다리는 동안 다른 요청이 커밋한
     * 발송 요청을 세지 못한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SendWindow lockSendsByIp(String requestIp) {
        requireRequestIp(requestIp);
        ipLockMapper.lock(requestIp, Instant.now(clock));
        return summarizeSentByIp(requestIp, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDuplicateSignup(String requestIp) {
        signupDuplicateAttemptMapper.insert(requestIp, Instant.now(clock));
    }

    private SendWindow summarizeSentByIp(String requestIp, Instant now) {
        Instant since = now.minus(EmailVerificationPolicy.IP_WINDOW);
        SendWindow issued = emailVerificationRepository.summarizeSentByIpSince(requestIp, since);
        SendWindow queued = emailVerificationMapper.summarizeQueuedSendsByIpSince(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                OutboxEventStatus.PENDING,
                requestIp,
                since);
        SendWindow duplicateSignups = signupDuplicateAttemptMapper.summarizeByIpSince(requestIp, since);
        return issued.plus(queued).plus(duplicateSignups);
    }
}
