package com.moewadeul.member.application.signup;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.application.MemberMetrics;
import com.moewadeul.member.application.MemberUniqueConstraintTranslator;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Nickname;
import com.moewadeul.member.domain.Password;
import com.moewadeul.member.domain.signup.DisposableEmailDomainRepository;
import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.domain.verification.SendWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private final MemberRepository memberRepository;
    private final DisposableEmailDomainRepository disposableEmailDomainRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final OutboxEventRecorder outboxEventRecorder;
    private final MemberMetrics memberMetrics;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public SignupResult signUp(SignupCommand command) {
        Password password = Password.of(command.password());
        Email email = Email.of(command.email());
        Nickname nickname = new Nickname(command.nickname());
        // IP 잠금이 이 트랜잭션의 첫 SQL이어야 한다. MySQL은 첫 일반 조회 때 읽기 스냅숏을 정하므로, 이보다 앞서 조회하면
        // 잠금을 기다리는 동안 같은 IP의 다른 가입이 커밋한 발송 요청을 세지 못한다.
        SendWindow sentByIp = emailVerificationService.lockSendsByIp(command.requestIp());
        Instant now = Instant.now(clock);
        rejectDisposableDomain(email);
        rejectDuplicates(email, nickname);
        rejectIfIpLimited(sentByIp, now);

        String passwordHash = passwordEncoder.encode(password.value());
        Member member = Member.register(email, passwordHash, nickname.value(), now);
        Member saved = saveOrTranslate(member);
        outboxEventRecorder.record(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                saved.getId(),
                new EmailVerificationEvents.Payload(command.requestIp()));
        memberMetrics.recordSignup();
        return new SignupResult(saved.getId(), saved.getStatus());
    }

    private void rejectIfIpLimited(SendWindow sentByIp, Instant now) {
        Optional<Duration> blockedFor =
                EmailVerificationPolicy.ipBlockedFor(Math.toIntExact(sentByIp.count()), sentByIp.oldestSentAt(), now);
        if (blockedFor.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.SIGNUP_LIMITED, blockedFor.get());
        }
    }

    private void rejectDisposableDomain(Email email) {
        if (disposableEmailDomainRepository.existsByDomainIn(email.domainCandidates())) {
            throw new MemberException(MemberErrorCode.MEMBER_DISPOSABLE_EMAIL);
        }
    }

    private void rejectDuplicates(Email email, Nickname nickname) {
        if (memberRepository.existsByEmail(email.value())) {
            throw new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
    }

    private Member saveOrTranslate(Member member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw MemberUniqueConstraintTranslator.translate(e);
        }
    }
}
