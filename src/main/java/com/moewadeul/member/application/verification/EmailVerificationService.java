package com.moewadeul.member.application.verification;

import static com.moewadeul.member.application.MemberGuards.memberNotFound;
import static com.moewadeul.member.application.MemberGuards.requireRequestIp;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.EmailVerificationEvents;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailSendIpLimiter emailSendIpLimiter;
    private final EmailVerificationApplier emailVerificationApplier;
    private final OutboxEventRecorder outboxEventRecorder;
    private final MemberMetrics memberMetrics;
    private final Clock clock;

    @Transactional
    public void requestResend(long memberId, String requestIp) {
        requireRequestIp(requestIp);
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(() -> memberNotFound(memberId));
        rejectIfAlreadyVerified(member);
        SendWindow sentByIp = emailSendIpLimiter.lockSendsByIp(requestIp);

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
        emailVerificationApplier.activate(verification.getId(), memberId, now);
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
        if (!emailVerificationApplier.countWrongAttempt(verification.getId(), now)) {
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

    private boolean hasPendingSendRequest(long memberId) {
        return outboxEventRecorder.hasPending(
                EmailVerificationEvents.EVENT_TYPE, EmailVerificationEvents.AGGREGATE_TYPE, memberId);
    }

    private void rejectIfAlreadyVerified(Member member) {
        if (!member.isAwaitingEmailVerification()) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
    }
}
