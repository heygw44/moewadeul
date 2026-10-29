package com.moewadeul.member.application.password;

import static com.moewadeul.member.application.MemberGuards.memberNotFound;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Password;
import com.moewadeul.member.domain.password.PasswordResetToken;
import com.moewadeul.member.domain.password.PasswordResetTokenRepository;
import com.moewadeul.member.domain.password.ResetToken;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PasswordResetApplier {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    long apply(String rawToken, String newPassword) {
        Password password = Password.of(newPassword);
        Instant now = Instant.now(clock);
        PasswordResetToken token = findUsableToken(rawToken, now);
        long memberId = token.getMemberId();
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(() -> memberNotFound(memberId));
        if (member.isWithdrawn()) {
            throw invalidToken();
        }
        if (passwordResetTokenMapper.markUsed(token.getId(), now) == 0) {
            throw invalidToken();
        }
        member.changePassword(passwordEncoder.encode(password.value()), now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
        return memberId;
    }

    private PasswordResetToken findUsableToken(String rawToken, Instant now) {
        if (!ResetToken.isWellFormed(rawToken)) {
            throw invalidToken();
        }
        return passwordResetTokenRepository
                .findByTokenHash(ResetToken.hash(rawToken))
                .filter(found -> found.isUsable(now))
                .orElseThrow(PasswordResetApplier::invalidToken);
    }

    private static BusinessException invalidToken() {
        return new BusinessException(MemberErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
}
