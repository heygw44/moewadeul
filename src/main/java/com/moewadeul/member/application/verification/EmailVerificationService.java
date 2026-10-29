package com.moewadeul.member.application.verification;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.outbox.OutboxEventJpaRepository;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.outbox.OutboxEventStatus;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.application.MailSecretDeriver;
import com.moewadeul.member.application.MemberMetrics;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.verification.EmailVerification;
import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.domain.verification.EmailVerificationPolicy.ResendHistory;
import com.moewadeul.member.domain.verification.EmailVerificationRepository;
import com.moewadeul.member.domain.verification.SendWindow;
import com.moewadeul.member.domain.verification.VerificationCode;
import com.moewadeul.member.infra.verification.EmailVerificationIpLockMapper;
import com.moewadeul.member.infra.verification.EmailVerificationMapper;
import com.moewadeul.member.infra.verification.SignupDuplicateAttemptMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationMapper emailVerificationMapper;
    private final EmailVerificationIpLockMapper ipLockMapper;
    private final SignupDuplicateAttemptMapper signupDuplicateAttemptMapper;
    private final EmailVerificationActivator emailVerificationActivator;
    private final OutboxEventRecorder outboxEventRecorder;
    private final OutboxEventJpaRepository outboxEventRepository;
    private final MemberMetrics memberMetrics;
    private final MailSecretDeriver mailSecretDeriver;
    private final Clock clock;

    @Transactional
    public Optional<IssuedVerification> issueFor(long memberId, String requestIp, long outboxEventId) {
        requireRequestIp(requestIp);
        return memberRepository
                .findById(memberId)
                .filter(EmailVerificationService::isUnverified)
                .map(member -> issue(member, requestIp, outboxEventId));
    }

    private IssuedVerification issue(Member member, String requestIp, long outboxEventId) {
        VerificationCode code = VerificationCode.fromSeed(
                mailSecretDeriver.derive(MailSecretDeriver.Purpose.EMAIL_VERIFICATION, outboxEventId, member.getId()));
        String codeHash = VerificationCode.hash(member.getId(), code.value());
        Instant now = Instant.now(clock);
        emailVerificationRepository
                .findByOutboxEventId(outboxEventId)
                .ifPresentOrElse(
                        existing -> existing.reissue(codeHash, now),
                        () -> emailVerificationRepository.save(
                                EmailVerification.issue(member.getId(), outboxEventId, codeHash, requestIp, now)));
        return new IssuedVerification(member.getEmail(), code.value());
    }

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

    @Transactional
    public void requestResend(long memberId, String requestIp) {
        requireRequestIp(requestIp);
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(() -> memberNotFound(memberId));
        rejectIfAlreadyVerified(member);
        SendWindow sentByIp = lockSendsByIp(requestIp);

        Instant now = Instant.now(clock);
        ResendHistory history = loadResendHistory(memberId, sentByIp, now);
        Optional<Duration> blockedFor = EmailVerificationPolicy.resendBlockedFor(history, now);
        if (blockedFor.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.EMAIL_RESEND_LIMITED, blockedFor.get());
        }
        outboxEventRecorder.record(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                memberId,
                new EmailVerificationEvents.Payload(requestIp));
    }

    public VerificationResult verify(long memberId, String code) {
        OutsideTransaction.require("트랜잭션 안이면 틀린 코드로 올린 시도 횟수가 예외와 함께 롤백된다.");
        Instant now = Instant.now(clock);
        Member member = memberRepository.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
        rejectIfAlreadyVerified(member);

        EmailVerification verification = findLatest(memberId, code);
        rejectIfExpiredOrExhausted(verification, now);
        if (!verification.matches(code)) {
            rejectWrongCode(verification, now);
        }
        emailVerificationActivator.activate(verification.getId(), memberId, now);
        log.info("email verified memberId={}", memberId);
        memberMetrics.recordEmailVerified();
        return new VerificationResult(MemberStatus.ACTIVE);
    }

    private EmailVerification findLatest(long memberId, String code) {
        if (!VerificationCode.isWellFormed(code)) {
            throw new BusinessException(MemberErrorCode.EMAIL_CODE_INVALID);
        }
        EmailVerification latest = emailVerificationRepository
                .findLatestByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.EMAIL_CODE_INVALID));
        if (latest.isVerified()) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        return latest;
    }

    private void rejectIfExpiredOrExhausted(EmailVerification verification, Instant now) {
        if (verification.isExpiredAt(now)) {
            throw new BusinessException(MemberErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (verification.hasReachedAttemptLimit()) {
            throw new BusinessException(MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
    }

    private void rejectWrongCode(EmailVerification verification, Instant now) {
        int updated = emailVerificationMapper.incrementAttempt(
                verification.getId(), EmailVerificationPolicy.MAX_ATTEMPTS, now);
        if (updated == 0) {
            throw new BusinessException(MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
        throw new BusinessException(MemberErrorCode.EMAIL_CODE_INVALID);
    }

    private ResendHistory loadResendHistory(long memberId, SendWindow sentByIp, Instant now) {
        SendWindow daily = emailVerificationRepository.summarizeSentToMemberSince(
                memberId, now.minus(EmailVerificationPolicy.DAILY_WINDOW));
        return new ResendHistory(
                emailVerificationRepository.findLastSentAt(memberId).orElse(null),
                Math.toIntExact(daily.count()),
                daily.oldestSentAt(),
                Math.toIntExact(sentByIp.count()),
                sentByIp.oldestSentAt(),
                hasPendingSendRequest(memberId));
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

    private boolean hasPendingSendRequest(long memberId) {
        return outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatus(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                memberId,
                OutboxEventStatus.PENDING);
    }

    private void rejectIfAlreadyVerified(Member member) {
        if (!isUnverified(member)) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
    }

    private static boolean isUnverified(Member member) {
        return member.getStatus() == MemberStatus.UNVERIFIED;
    }

    private static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("인증을 요청한 회원이 없습니다. memberId=" + memberId);
    }

    private static void requireRequestIp(String requestIp) {
        if (requestIp == null || requestIp.isBlank()) {
            throw new IllegalArgumentException("요청 IP가 비어 있습니다.");
        }
    }
}
