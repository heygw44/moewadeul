package com.moewadeul.member.application.login;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.application.LoginAttemptService;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.MemberSuspendedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberLoginService {

    private static final int ATTEMPTED_EMAIL_MAX_LENGTH = 254;

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final MemberSuspensionService memberSuspensionService;
    private final MemberSessions memberSessions;
    private final Clock clock;

    private final String timingDecoyPasswordHash;

    public MemberLoginService(
            MemberRepository memberRepository,
            LoginAttemptService loginAttemptService,
            PasswordEncoder passwordEncoder,
            MemberSuspensionService memberSuspensionService,
            MemberSessions memberSessions,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.loginAttemptService = loginAttemptService;
        this.passwordEncoder = passwordEncoder;
        this.memberSuspensionService = memberSuspensionService;
        this.memberSessions = memberSessions;
        this.clock = clock;
        this.timingDecoyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResult login(LoginCommand command) {
        String attemptedEmail = normalizeEmail(command.email());
        rejectIfLocked(attemptedEmail);
        Member authenticated = authenticate(attemptedEmail, command);
        Member member = releaseIfSuspensionEnded(authenticated);
        rejectIfSuspended(member);
        loginAttemptService.recordSuccess(member.getId(), attemptedEmail, command.ip());
        return new LoginResult(
                member.getId(),
                member.getNickname(),
                member.getStatus(),
                member.getRole(),
                member.getPasswordChangedAt());
    }

    public void ensureStillLoginable(LoginResult loggedIn) {
        Member current = memberRepository
                .findById(loggedIn.memberId())
                .filter(found -> !found.isWithdrawn())
                .filter(found -> Objects.equals(found.getPasswordChangedAt(), loggedIn.passwordChangedAt()))
                .orElseThrow(() -> new BusinessException(MemberErrorCode.LOGIN_FAILED));
        rejectIfSuspended(current);
    }

    public void revokeSessionIfMemberChanged(LoginResult loggedIn, String sessionId) {
        try {
            ensureStillLoginable(loggedIn);
        } catch (BusinessException e) {
            memberSessions.invalidate(sessionId);
        }
    }

    private static String normalizeEmail(String rawEmail) {
        try {
            return Email.of(rawEmail).value();
        } catch (IllegalArgumentException e) {
            String lowerCased = rawEmail.toLowerCase(Locale.ROOT);
            return lowerCased.substring(0, Math.min(lowerCased.length(), ATTEMPTED_EMAIL_MAX_LENGTH));
        }
    }

    private void rejectIfLocked(String attemptedEmail) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(attemptedEmail);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    private Member authenticate(String attemptedEmail, LoginCommand command) {
        Member member = memberRepository
                .findByEmail(attemptedEmail)
                .filter(found -> !found.isWithdrawn())
                .orElse(null);
        String passwordHash = member != null ? member.getPasswordHash() : timingDecoyPasswordHash;
        boolean authenticated = passwordEncoder.matches(command.password(), passwordHash) && member != null;
        Long memberId = member != null ? member.getId() : null;
        loginAttemptService.recordAttemptUnderEmailLock(memberId, attemptedEmail, command.ip(), authenticated);
        if (!authenticated) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    private Member releaseIfSuspensionEnded(Member member) {
        if (member.getStatus() != MemberStatus.SUSPENDED || member.getSuspendedUntil() == null) {
            return member;
        }
        Instant now = clock.instant();
        if (member.getSuspendedUntil().isAfter(now)) {
            return member;
        }
        memberSuspensionService.releaseIfExpired(member.getId());
        return memberRepository.findById(member.getId()).orElse(member);
    }

    private void rejectIfSuspended(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new MemberSuspendedException(member.getSuspendedUntil());
        }
    }
}
