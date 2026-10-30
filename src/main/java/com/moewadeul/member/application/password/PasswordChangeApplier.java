package com.moewadeul.member.application.password;

import static com.moewadeul.member.application.MemberGuards.memberNotFound;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
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
    void apply(long memberId, String verifiedPasswordHash, String newPasswordHash) {
        Instant now = Instant.now(clock);
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(() -> memberNotFound(memberId));
        if (!Objects.equals(member.getPasswordHash(), verifiedPasswordHash)) {
            throw new BusinessException(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH);
        }
        member.changePassword(newPasswordHash, now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
    }
}
