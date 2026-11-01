package com.moewadeul.member.application.password;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.LoginAttemptService;
import com.moewadeul.member.application.MemberSessionCleanup;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Password;
import java.time.Duration;
import java.time.Instant;
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
    private final MemberSessionCleanup memberSessionCleanup;

    public Instant change(PasswordChangeCommand command) {
        OutsideTransaction.require("트랜잭션 안이면 틀린 비밀번호 기록이 예외와 함께 롤백되고, 세션은 변경이 커밋된 뒤에 지워야 한다.");
        Member member = findChangeableMember(command.memberId());
        rejectIfLocked(member);
        verifyCurrentPassword(member, command);
        Password newPassword = Password.of(command.newPassword());
        if (passwordEncoder.matches(newPassword.value(), member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.MEMBER_PASSWORD_UNCHANGED);
        }
        Instant passwordChangedAt = passwordChangeApplier.apply(
                member.getId(), member.getPasswordHash(), passwordEncoder.encode(newPassword.value()));
        memberSessionCleanup.invalidateOthersAfterCommit(member.getId(), command.currentSessionId());
        log.info("password changed memberId={}", member.getId());
        return passwordChangedAt;
    }

    private Member findChangeableMember(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(Member::canAuthenticateWithPassword)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH));
    }

    private void rejectIfLocked(Member member) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(member.getId(), member.getEmail());
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
