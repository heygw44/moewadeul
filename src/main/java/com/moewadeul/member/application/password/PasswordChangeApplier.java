package com.moewadeul.member.application.password;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Password;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeApplier {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void apply(long memberId, Password newPassword) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
        member.changePassword(passwordEncoder.encode(newPassword.value()), Instant.now(clock));
    }

    private static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("비밀번호를 바꿀 회원이 없습니다. memberId=" + memberId);
    }
}
