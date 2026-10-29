package com.moewadeul.member.application.password;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Password;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PasswordChangeApplier {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    void apply(long memberId, Password newPassword) {
        Instant now = Instant.now(clock);
        Member member = memberRepository.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
        member.changePassword(passwordEncoder.encode(newPassword.value()), now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
    }

    private static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("비밀번호를 바꿀 회원이 없습니다. memberId=" + memberId);
    }
}
