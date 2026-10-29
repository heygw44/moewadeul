package com.moewadeul.member.application.verification;

import static com.moewadeul.member.application.MemberGuards.requireRequestIp;

import com.moewadeul.member.application.MailSecretDeriver;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.verification.EmailVerification;
import com.moewadeul.member.domain.verification.EmailVerificationRepository;
import com.moewadeul.member.domain.verification.VerificationCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmailVerificationIssuer {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final MailSecretDeriver mailSecretDeriver;
    private final Clock clock;

    @Transactional
    public Optional<IssuedVerification> issueFor(long memberId, String requestIp, long outboxEventId) {
        requireRequestIp(requestIp);
        return memberRepository
                .findById(memberId)
                .filter(Member::isAwaitingEmailVerification)
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
}
