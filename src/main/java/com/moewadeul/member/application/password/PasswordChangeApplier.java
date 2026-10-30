package com.moewadeul.member.application.password;

import static com.moewadeul.member.application.MemberGuards.memberNotFound;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PasswordChangeApplier {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final Clock clock;

    @Transactional
    void apply(long memberId, String newPasswordHash) {
        Instant now = Instant.now(clock);
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(() -> memberNotFound(memberId));
        member.changePassword(newPasswordHash, now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
    }
}
