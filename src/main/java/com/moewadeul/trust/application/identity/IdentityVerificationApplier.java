package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.trust.application.profile.TrustSummary;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.identity.CiHash;
import com.moewadeul.trust.domain.identity.CiHasher;
import com.moewadeul.trust.domain.identity.IdentityClaim;
import com.moewadeul.trust.domain.identity.IdentityProvider;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IdentityVerificationApplier {

    private final IdentityVerificationRepository identityVerificationRepository;
    private final IdentityProvider identityProvider;
    private final CiHasher ciHasher;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    @Transactional
    IdentityVerificationResult apply(long memberId, IdentityVerifyCommand command) {
        Instant now = clock.instant();
        Year currentYear = Year.from(now.atZone(KoreaTime.ZONE));
        requireBirthYearInRange(command.birthYear(), currentYear);
        if (identityVerificationRepository.existsByMemberId(memberId)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        VerifiedIdentity identity = identityProvider.verify(
                new IdentityClaim(command.birthYear(), command.gender(), command.demoIdentityKey()));
        CiHash ciHash = ciHasher.hash(identity.ci());
        if (identityVerificationRepository.existsByCiHash(ciHash)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
        }
        IdentityVerification saved =
                save(IdentityVerification.verify(memberId, identity, ciHash, identityProvider.type(), now));
        TrustSummary summary = trustSummaryService.summarizeOf(saved, memberId, now);
        return new IdentityVerificationResult(
                summary.identityVerified(), saved.isAdult(currentYear), summary.trustLevel());
    }

    private IdentityVerification save(IdentityVerification verification) {
        try {
            return identityVerificationRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException e) {
            throw IdentityUniqueConstraintTranslator.translate(e);
        }
    }

    private static void requireBirthYearInRange(int birthYear, Year currentYear) {
        if (birthYear < IdentityVerificationService.MIN_BIRTH_YEAR || birthYear > currentYear.getValue()) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "출생연도는 " + IdentityVerificationService.MIN_BIRTH_YEAR + "년부터 올해까지여야 해요.");
        }
    }
}
