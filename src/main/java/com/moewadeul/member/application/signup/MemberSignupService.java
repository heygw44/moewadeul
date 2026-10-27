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
        Member.requireValidNickname(command.nickname());
        // IP 잠금이 이 트랜잭션의 첫 SQL이어야 한다. MySQL은 첫 일반 조회 때 읽기 스냅숏을 정하므로, 이보다 앞서 조회하면
        // 잠금을 기다리는 동안 같은 IP의 다른 가입이 커밋한 발송 요청을 세지 못한다.
        SendWindow sentByIp = emailVerificationService.lockSendsByIp(command.requestIp());
        Instant now = Instant.now(clock);
        rejectDisposableDomain(email);
        rejectDuplicates(email, command.nickname());
        rejectIfIpLimited(sentByIp, now);

        String passwordHash = passwordEncoder.encode(password.value());
        Member member = Member.register(email, passwordHash, command.nickname(), now);
        Member saved = saveOrTranslate(member);
        outboxEventRecorder.record(
                EmailVerificationEvents.EVENT_TYPE,
                EmailVerificationEvents.AGGREGATE_TYPE,
                saved.getId(),
                new EmailVerificationEvents.Payload(command.requestIp()));
        memberMetrics.recordSignup();
        return new SignupResult(saved.getId(), saved.getStatus());
    }

    // 가입의 첫 인증 메일도 재발송과 같은 IP 시간당 한도를 쓴다. 한 IP가 가입을 쏟아내서 메일 서비스의 하루 발송 한도를 다 쓰고
    // 다른 회원의 인증·재설정 메일까지 못 나가게 하는 일을 막는다. 비용이 큰 비밀번호 해시 전에 거른다.
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

    private void rejectDuplicates(Email email, String nickname) {
        if (memberRepository.existsByEmail(email.value())) {
            throw new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
    }

    // 사전 조회와 저장 사이에 다른 요청이 같은 이메일이나 닉네임을 먼저 넣을 수 있다.
    // 이때는 서비스가 DB 유니크 제약 위반을 이메일 중복이나 닉네임 중복 오류로 바꿔 던진다.
    private Member saveOrTranslate(Member member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw MemberUniqueConstraintTranslator.translate(e);
        }
    }
}
