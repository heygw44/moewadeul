package com.moewadeul.member.application.verification;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.outbox.OutboxEventJpaRepository;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.outbox.OutboxEventStatus;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.application.MemberMetrics;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
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
import com.moewadeul.member.infra.verification.MemberActivationMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 이메일 인증 유스케이스: 코드 발급, 재발송 요청, 코드 확인.
 *
 * <p>코드 원값은 {@link #issueFor}가 돌려주는 {@link IssuedVerification}에만 있다. DB에는 해시만 저장하고,
 * 로그와 예외 메시지와 이벤트 payload에는 코드도 이메일도 싣지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationMapper emailVerificationMapper;
    private final EmailVerificationIpLockMapper ipLockMapper;
    private final MemberActivationMapper memberActivationMapper;
    private final OutboxEventRecorder outboxEventRecorder;
    private final OutboxEventJpaRepository outboxEventRepository;
    private final MemberMetrics memberMetrics;
    private final Clock clock;

    // SecureRandom은 스레드에 안전하다. 코드는 추측할 수 없어야 하므로 Random을 쓰지 않는다.
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 호출하면 발송 요청 이벤트 하나에 코드 행을 하나만 두고, 새 인증 코드를 만들어 해시만 저장한 뒤 코드 원값과 받을 이메일을 돌려준다.
     * 같은 이벤트를 다시 처리하면(메일 발송이 실패해 발행기가 다시 집은 경우 등) 새 행을 만들지 않고 같은 행의 코드를 바꾼다.
     * 그래서 메일 서버가 멈춘 동안 재시도해도 재발송 한도에 드는 횟수가 늘지 않는다. 바꾼 뒤에는 새 코드만 유효하다.
     * 회원이 없거나 이미 인증을 마쳤거나 탈퇴했으면 아무것도 저장하지 않고 빈 값을 돌려준다.
     * 다른 이벤트로 만든 이전 코드는 지우지 않지만 가장 나중에 발급한 코드만 {@link #verify}에서 유효하다.
     */
    @Transactional
    public Optional<IssuedVerification> issueFor(long memberId, String requestIp, long outboxEventId) {
        requireRequestIp(requestIp);
        return memberRepository
                .findById(memberId)
                .filter(EmailVerificationService::isUnverified)
                .map(member -> issue(member, requestIp, outboxEventId));
    }

    private IssuedVerification issue(Member member, String requestIp, long outboxEventId) {
        VerificationCode code = VerificationCode.generate(secureRandom);
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
     * 호출하면 그 IP의 발송 잠금 행을 쓰기 잠금으로 잡은 뒤, 그 IP가 최근 한 시간에 요청한 인증 메일 발송 수를 센다.
     * 세는 대상은 이미 코드 행을 만든 발송과 아직 코드 행이 없는 대기 중 발송 요청이다.
     *
     * <p>같은 IP로 이 메서드를 부른 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다. 그래서 호출한 쪽이 이 수로 판정하고
     * 발송 요청을 기록해 커밋할 때까지 같은 IP의 다른 요청이 끼어들지 못한다. 가입의 첫 발송과 재발송이 이 잠금을 함께 쓴다.
     * 한 시간 구간은 잠금을 얻은 뒤의 시각으로 정한다. 기다린 시간만큼 구간이 뒤처지면 그사이 구간을 벗어난 발송까지 세기 때문이다.
     * 판정에 쓸 현재 시각도 호출한 쪽이 이 메서드가 끝난 뒤에 읽는다.
     *
     * <p>호출하는 쪽의 트랜잭션에서 잠그지 않는 조회를 하기 전에 부른다.
     * MySQL은 트랜잭션의 첫 일반 조회 때 읽기 스냅숏을 정한다. 그래서 먼저 조회하면, 잠금을 기다리는 동안 다른 요청이 커밋한
     * 발송 요청을 세지 못한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SendWindow lockSendsByIp(String requestIp) {
        requireRequestIp(requestIp);
        ipLockMapper.lock(requestIp, Instant.now(clock));
        return summarizeSentByIp(requestIp, Instant.now(clock));
    }

    /**
     * 호출하면 재발송 한도를 검사하고, 통과하면 코드 발송 요청 이벤트를 기록한다. 코드는 이벤트 처리기가 만든다.
     *
     * @throws RateLimitedException 간격이나 하루·IP별 한도에 걸렸을 때. 풀리기까지 남은 시간을 담는다.
     */
    @Transactional
    public void requestResend(long memberId, String requestIp) {
        requireRequestIp(requestIp);
        // 회원 행을 잠가서 같은 회원의 재발송 요청을 한 줄로 세운다. 잠그지 않으면 동시에 온 요청이 모두 "이력 없음"으로 보고
        // 한도 검사를 통과해 이벤트가 여러 건 쌓인다. 이어서 IP 잠금 행을 잡아 같은 IP의 가입과 재발송도 한 줄로 세운다.
        // 잠금 순서는 늘 회원 행이 먼저, IP 행이 나중이고, 이력 조회는 두 잠금을 모두 얻은 뒤에 한다. 그래야 이력 조회가
        // 잠금을 얻은 뒤의 스냅샷을 읽어서, 먼저 끝난 요청이 기록한 이벤트가 보인다.
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

    /**
     * 호출하면 입력한 코드를 가장 나중에 발급한 코드와 비교해, 맞으면 회원을 ACTIVE로 바꾼다.
     *
     * <p>{@code noRollbackFor}를 두는 이유: 코드가 틀리면 시도 횟수를 올린 뒤 {@link MemberException}을 던진다.
     * 예외가 트랜잭션 밖으로 나가면 기본 설정은 롤백이라서 올린 횟수도 사라지고, 그러면 아무리 틀려도 5번 제한에 닿지 않는다.
     * 그래서 이 예외로는 롤백하지 않는다. 다만 아래에서 상태가 어긋난 채 커밋되면 안 되는 곳은 직접 롤백을 표시한다.
     */
    @Transactional(noRollbackFor = MemberException.class)
    public VerificationResult verify(long memberId, String code) {
        Instant now = Instant.now(clock);
        Member member = memberRepository.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
        rejectIfAlreadyVerified(member);

        EmailVerification verification = findLatestUnverified(memberId, code);
        rejectIfExpiredOrExhausted(verification, now);
        if (!verification.matches(code)) {
            rejectWrongCode(verification, now);
        }
        activate(verification, memberId, now);
        return new VerificationResult(MemberStatus.ACTIVE);
    }

    // 코드 형식이 틀린 입력은 시도로 세지 않는다. 숫자 6자리가 아니면 어떤 코드와도 같을 수 없어서 추측으로 볼 수 없기 때문이다.
    private EmailVerification findLatestUnverified(long memberId, String code) {
        if (!VerificationCode.isWellFormed(code)) {
            throw new MemberException(MemberErrorCode.EMAIL_CODE_INVALID);
        }
        return emailVerificationRepository
                .findLatestByMemberId(memberId)
                .filter(found -> !found.isVerified())
                .orElseThrow(() -> new MemberException(MemberErrorCode.EMAIL_CODE_INVALID));
    }

    // 시도 횟수가 한도에 닿은 코드는 맞는 코드를 넣어도 거부한다. 그렇지 않으면 다섯 번을 넘겨 계속 추측할 수 있다.
    private void rejectIfExpiredOrExhausted(EmailVerification verification, Instant now) {
        if (verification.isExpiredAt(now)) {
            throw new MemberException(MemberErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (verification.hasReachedAttemptLimit()) {
            throw new MemberException(MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
    }

    // 읽어 둔 시도 횟수에 1을 더해 저장하면 동시에 틀린 요청들이 같은 값을 읽고 같은 값을 써서 횟수가 덜 올라간다.
    // 그래서 DB가 한도 조건을 검사하며 올리게 하고, 0행이면 다른 요청이 먼저 한도를 채운 것으로 본다.
    private void rejectWrongCode(EmailVerification verification, Instant now) {
        int updated = emailVerificationMapper.incrementAttempt(
                verification.getId(), EmailVerificationPolicy.MAX_ATTEMPTS, now);
        if (updated == 0) {
            throw new MemberException(MemberErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
        throw new MemberException(MemberErrorCode.EMAIL_CODE_INVALID);
    }

    // 같은 코드를 동시에 보낸 요청 중 코드 행을 먼저 인증 처리한 쪽만 이긴다. 진 쪽은 조건에 맞는 행이 없어 0행을 받는다.
    // 이 메서드가 갱신한 회원 엔티티는 읽어 둔 값과 달라졌으므로 이후에 쓰지 않는다.
    private void activate(EmailVerification verification, long memberId, Instant now) {
        int verified =
                emailVerificationMapper.markVerified(verification.getId(), EmailVerificationPolicy.MAX_ATTEMPTS, now);
        if (verified == 0) {
            throw new MemberException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        if (memberActivationMapper.activate(memberId, now) == 0) {
            // 코드 행은 이미 인증 처리했는데 회원 상태가 바뀌지 않았다. noRollbackFor 때문에 그대로 커밋하면
            // 인증된 코드와 미인증 회원이 남으므로, 이 트랜잭션은 직접 롤백한다.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new MemberException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        log.info("email verified memberId={}", memberId);
        memberMetrics.recordEmailVerified();
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

    // 이미 코드 행을 만든 요청과 아직 코드 행이 없는 대기 요청은 서로 겹치지 않는다. 그래서 두 수를 더해도 한 요청을 두 번 세지 않는다.
    private SendWindow summarizeSentByIp(String requestIp, Instant now) {
        Instant since = now.minus(EmailVerificationPolicy.IP_WINDOW);
        SendWindow issued = emailVerificationRepository.summarizeSentByIpSince(requestIp, since);
        SendWindow queued = emailVerificationMapper.summarizeQueuedSendsByIpSince(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                OutboxEventStatus.PENDING,
                requestIp,
                since);
        return issued.plus(queued);
    }

    private boolean hasPendingSendRequest(long memberId) {
        return outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatus(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                memberId,
                OutboxEventStatus.PENDING);
    }

    // 상태가 UNVERIFIED가 아니면 인증을 요청할 이유가 없다. 인증을 마친 뒤 정지된 회원도 같은 응답을 받는다.
    private void rejectIfAlreadyVerified(Member member) {
        if (!isUnverified(member)) {
            throw new MemberException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
    }

    private static boolean isUnverified(Member member) {
        return member.getStatus() == MemberStatus.UNVERIFIED;
    }

    // 세션은 있는데 회원 행이 없는 경우다. 미인증 계정 정리가 회원을 지우는 사이에 요청이 들어오면 생긴다.
    // 알맞은 오류 코드가 없고 클라이언트가 고칠 수도 없어서 서버 오류로 다룬다. 회원 ID만 메시지에 싣는다.
    private static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("인증을 요청한 회원이 없습니다. memberId=" + memberId);
    }

    private static void requireRequestIp(String requestIp) {
        if (requestIp == null || requestIp.isBlank()) {
            throw new IllegalArgumentException("요청 IP가 비어 있습니다.");
        }
    }
}
