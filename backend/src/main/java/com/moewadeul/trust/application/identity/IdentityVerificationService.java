package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.identity.IdentityClaim;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 이 클래스에는 {@code @Transactional}을 두지 않는다. 본인확인 업체 응답을 기다리는 동안 DB 커넥션을 쥐지 않고, 잠금 실패로 롤백된 트랜잭션을 밖에서 최대
 * {@value #MAX_ATTEMPTS}번까지 다시 실행하려는 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    private static final int MIN_BIRTH_YEAR = 1900;

    private static final int MAX_ATTEMPTS = 3;

    private final IdentityVerificationApplier identityVerificationApplier;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final ObjectProvider<IdentityProvider> identityProviders;
    private final Clock clock;

    public IdentityVerificationResult verify(long memberId, IdentityVerifyCommand command) {
        OutsideTransaction.require("본인확인 업체 응답을 기다리는 동안 DB 커넥션을 쥐면 안 된다.");
        IdentityProvider identityProvider = identityProviders.getIfAvailable();
        if (identityProvider == null) {
            throw new BusinessException(TrustErrorCode.IDENTITY_UNAVAILABLE);
        }
        requireBirthYearInRange(command.birthYear(), Year.from(clock.instant().atZone(KoreaTime.ZONE)));
        if (identityVerificationRepository.existsByMemberId(memberId)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        VerifiedIdentity identity = identityProvider.verify(
                new IdentityClaim(command.birthYear(), command.gender(), command.demoIdentityKey()));
        for (int attempt = 1; ; attempt++) {
            try {
                return identityVerificationApplier.apply(memberId, identity, identityProvider.type());
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("identity verification lock failure, retrying memberId={} attempt={}", memberId, attempt);
            }
        }
    }

    private static void requireBirthYearInRange(int birthYear, Year currentYear) {
        if (birthYear < MIN_BIRTH_YEAR || birthYear > currentYear.getValue()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "출생연도는 " + MIN_BIRTH_YEAR + "년부터 올해까지여야 해요.");
        }
    }
}
