package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.member.application.MemberWithdrawalService;
import com.moewadeul.trust.application.profile.TrustSummary;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.identity.CiHash;
import com.moewadeul.trust.domain.identity.IdentityProviderType;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;
import com.moewadeul.trust.infra.identity.IdentityVerificationInsertMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IdentityVerificationApplier {

    private final MemberWithdrawalService memberWithdrawalService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final IdentityVerificationInsertMapper identityVerificationInsertMapper;
    private final CiHasher ciHasher;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    @Transactional
    IdentityVerificationResult apply(long memberId, VerifiedIdentity identity, IdentityProviderType providerType) {
        memberWithdrawalService.lockSharedUnlessWithdrawn(memberId);
        if (identityVerificationRepository.existsByMemberId(memberId)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        Instant now = clock.instant();
        CiHash ciHash = ciHasher.hash(identity.ci());
        if (identityVerificationRepository.existsByCiHash(ciHash)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
        }
        identityVerificationInsertMapper.insertIgnoringDuplicate(
                IdentityVerification.verify(memberId, identity, ciHash, providerType, now));
        IdentityVerification saved =
                identityVerificationRepository.findByMemberId(memberId).orElseThrow(() -> conflictOf(memberId));
        TrustSummary summary = trustSummaryService.summarizeOf(saved, memberId, now);
        return new IdentityVerificationResult(
                summary.identityVerified(), saved.isAdult(Year.from(now.atZone(KoreaTime.ZONE))), summary.trustLevel());
    }

    private BusinessException conflictOf(long memberId) {
        return new BusinessException(
                identityVerificationInsertMapper.existsByMemberIdCommitted(memberId)
                        ? TrustErrorCode.IDENTITY_ALREADY_VERIFIED
                        : TrustErrorCode.IDENTITY_CI_DUPLICATED);
    }
}
