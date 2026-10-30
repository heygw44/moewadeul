package com.moewadeul.member.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final Clock clock;

    public void verifyPassword(long memberId, String password, String ip) {
        OutsideTransaction.require("트랜잭션 안이면 틀린 비밀번호 기록이 예외와 함께 롤백되어 로그인 잠금이 걸리지 않는다.");
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.LOGIN_FAILED));
        // 이미 탈퇴했으면 비밀번호 해시가 지워져 확인할 수 없고, 요청한 결과도 이미 이뤄졌다.
        if (member.isWithdrawn()) {
            return;
        }
        if (!member.canAuthenticateWithPassword()) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
        rejectIfLocked(member);
        boolean matched = passwordEncoder.matches(password, member.getPasswordHash());
        loginAttemptService.recordAttemptUnderEmailLock(member.getId(), member.getEmail(), ip, matched);
        if (!matched) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    private void rejectIfLocked(Member member) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(member.getId(), member.getEmail());
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndWithdraw(long memberId) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.LOGIN_FAILED));
        if (member.isWithdrawn()) {
            return false;
        }
        Instant now = clock.instant();
        member.withdraw(now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<Instant> findWithdrawnAt(long memberId) {
        return memberRepository.findById(memberId).filter(Member::isWithdrawn).map(Member::getWithdrawnAt);
    }
}
