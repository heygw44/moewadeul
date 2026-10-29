package com.moewadeul.member.application.password;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.application.LoginAttemptService;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.Password;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordChangeApplier passwordChangeApplier;
    private final MemberSessions memberSessions;

    public void change(PasswordChangeCommand command) {
        Member member = findChangeableMember(command.memberId());
        rejectIfLocked(member.getEmail());
        verifyCurrentPassword(member, command);
        Password newPassword = Password.of(command.newPassword());
        if (passwordEncoder.matches(newPassword.value(), member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.MEMBER_PASSWORD_UNCHANGED);
        }
        passwordChangeApplier.apply(member.getId(), newPassword);
        memberSessions.invalidateAllExcept(member.getId(), command.currentSessionId());
        log.info("password changed memberId={}", member.getId());
    }

    private Member findChangeableMember(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN && found.getPasswordHash() != null)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH));
    }

    private void rejectIfLocked(String email) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(email);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    private void verifyCurrentPassword(Member member, PasswordChangeCommand command) {
        boolean matched = passwordEncoder.matches(command.currentPassword(), member.getPasswordHash());
        loginAttemptService.recordAttemptUnderEmailLock(member.getId(), member.getEmail(), command.ip(), matched);
        if (!matched) {
            throw new BusinessException(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH);
        }
    }
}
