package com.moewadeul.member.application.verification;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.infra.verification.EmailVerificationMapper;
import com.moewadeul.member.infra.verification.MemberActivationMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class EmailVerificationActivator {

    private final EmailVerificationMapper emailVerificationMapper;
    private final MemberActivationMapper memberActivationMapper;

    @Transactional
    public void activate(long verificationId, long memberId, Instant now) {
        if (emailVerificationMapper.markVerified(verificationId, EmailVerificationPolicy.MAX_ATTEMPTS, now) == 0) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        if (memberActivationMapper.activate(memberId, now) == 0) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_VERIFIED);
        }
    }
}
