package com.moewadeul.member.application.password;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.MailSecretDeriver;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.password.PasswordResetRequestPolicy;
import com.moewadeul.member.domain.password.PasswordResetThrottle;
import com.moewadeul.member.domain.password.PasswordResetThrottleRepository;
import com.moewadeul.member.domain.password.PasswordResetToken;
import com.moewadeul.member.domain.password.PasswordResetTokenRepository;
import com.moewadeul.member.domain.password.ResetToken;
import com.moewadeul.member.infra.password.PasswordResetThrottleMapper;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetRequestService {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final PasswordResetThrottleRepository throttleRepository;
    private final PasswordResetThrottleMapper throttleMapper;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final MailSecretDeriver mailSecretDeriver;
    private final Clock clock;

    @Transactional
    public void request(String email, String requestIp) {
        requireRequestIp(requestIp);
        Instant now = Instant.now(clock);
        String normalized = Email.of(email).value();

        PasswordResetThrottle ipRow = lockRow(PasswordResetRequestPolicy.ipKey(requestIp), now);
        PasswordResetThrottle emailRow = lockRow(PasswordResetRequestPolicy.emailKey(normalized), now);

        Optional<Duration> blockedFor = longest(
                ipRow.blockedFor(PasswordResetRequestPolicy.IP, now),
                emailRow.blockedFor(PasswordResetRequestPolicy.EMAIL, now));
        if (blockedFor.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.PASSWORD_RESET_LIMITED, blockedFor.get());
        }
        ipRow.record(PasswordResetRequestPolicy.IP, now);
        emailRow.record(PasswordResetRequestPolicy.EMAIL, now);

        memberRepository
                .findByEmail(normalized)
                .filter(PasswordResetRequestService::isTarget)
                .ifPresent(member -> outboxEventRecorder.record(
                        PasswordResetEvents.EVENT_TYPE,
                        PasswordResetEvents.AGGREGATE_TYPE,
                        member.getId(),
                        new PasswordResetEvents.Payload()));
    }

    private PasswordResetThrottle lockRow(String key, Instant now) {
        throttleMapper.insertIfAbsent(key, now);
        return throttleRepository.findByKeyForUpdate(key).orElseThrow(PasswordResetRequestService::throttleRowMissing);
    }

    private static Optional<Duration> longest(Optional<Duration> first, Optional<Duration> second) {
        if (first.isPresent() && second.isPresent()) {
            return first.get().compareTo(second.get()) >= 0 ? first : second;
        }
        return first.isPresent() ? first : second;
    }

    private static IllegalStateException throttleRowMissing() {
        return new IllegalStateException("재설정 요청 한도 행을 만들었는데 읽을 수 없습니다.");
    }

    private static void requireRequestIp(String requestIp) {
        if (requestIp == null || requestIp.isBlank()) {
            throw new IllegalArgumentException("요청 IP가 비어 있습니다.");
        }
    }

    @Transactional
    public Optional<IssuedPasswordReset> issueFor(long memberId, long outboxEventId) {
        return memberRepository
                .findById(memberId)
                .filter(PasswordResetRequestService::isTarget)
                .map(member -> issue(member, outboxEventId));
    }

    // 살아 있는 링크는 마지막으로 보낸 요청의 것 하나만 둔다.
    private IssuedPasswordReset issue(Member member, long outboxEventId) {
        ResetToken token = ResetToken.fromSeed(
                mailSecretDeriver.derive(MailSecretDeriver.Purpose.PASSWORD_RESET, outboxEventId, member.getId()));
        String tokenHash = ResetToken.hash(token.value());
        Instant now = Instant.now(clock);
        passwordResetTokenRepository
                .findByTokenHash(tokenHash)
                .ifPresentOrElse(resent -> resent.renewIfUnused(now), () -> {
                    passwordResetTokenMapper.invalidateUnusedTokens(member.getId(), now);
                    passwordResetTokenRepository.save(PasswordResetToken.issue(member.getId(), tokenHash, now));
                });
        return new IssuedPasswordReset(member.getEmail(), token.value());
    }

    private static boolean isTarget(Member member) {
        return member.getStatus() != MemberStatus.WITHDRAWN;
    }
}
