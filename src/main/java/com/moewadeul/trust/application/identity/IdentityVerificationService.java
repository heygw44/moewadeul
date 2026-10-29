package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.identity.IdentityClaim;
import com.moewadeul.trust.domain.identity.IdentityProvider;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

/**
 * 이 클래스에는 {@code @Transactional}을 두지 않는다. 같은 CI로 여러 요청이 동시에 저장하면, MySQL이 유니크 인덱스의 중복 검사에서 잠금을
 * 기다리다 교착 상태를 감지하고 그중 한 트랜잭션을 롤백할 수 있다. 그래서 서비스는 롤백된 트랜잭션 밖에서 최대 {@value #MAX_ATTEMPTS}번까지 실행한다.
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
            } catch (CannotAcquireLockException e) {
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
