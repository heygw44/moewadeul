package com.moewadeul.member.application.signup;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.application.MemberMetrics;
import com.moewadeul.member.application.MemberUniqueConstraintTranslator;
import com.moewadeul.member.application.verification.EmailSendIpLimiter;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
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
    private final EmailSendIpLimiter emailSendIpLimiter;

    // 중복으로 거절한 가입 시도도 IP 한도에 세려면 그 기록을 커밋해야 한다.
    // 중복 예외는 회원을 저장하기 전에만 던지므로 롤백하지 않아도 회원이나 발송 요청이 남지 않는다.
    @Transactional(noRollbackFor = DuplicateSignupException.class)
    public SignupResult signUp(SignupCommand command) {
        Password password = Password.of(command.password());
        Email email = Email.of(command.email());
        Nickname nickname = new Nickname(command.nickname());
        SendWindow sentByIp = emailSendIpLimiter.lockSendsByIp(command.requestIp());
        Instant now = Instant.now(clock);
        rejectDisposableDomain(email);
        rejectIfIpLimited(sentByIp, now);
        rejectDuplicates(email, nickname, command.requestIp());

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
            throw new BusinessException(MemberErrorCode.MEMBER_DISPOSABLE_EMAIL);
        }
    }

    private void rejectDuplicates(Email email, Nickname nickname, String requestIp) {
        if (memberRepository.existsByEmail(email.value())) {
            throw duplicateRecorded(MemberErrorCode.MEMBER_EMAIL_DUPLICATED, requestIp);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw duplicateRecorded(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED, requestIp);
        }
    }

    private DuplicateSignupException duplicateRecorded(MemberErrorCode errorCode, String requestIp) {
        emailSendIpLimiter.recordDuplicateSignup(requestIp);
        return new DuplicateSignupException(errorCode);
    }

    private Member saveOrTranslate(Member member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw MemberUniqueConstraintTranslator.translate(e);
        }
    }

    private static final class DuplicateSignupException extends BusinessException {

        private DuplicateSignupException(MemberErrorCode errorCode) {
            super(errorCode);
        }
    }
}
