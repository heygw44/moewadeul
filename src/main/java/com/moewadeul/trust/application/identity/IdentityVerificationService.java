package com.moewadeul.trust.application.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    static final int MIN_BIRTH_YEAR = 1900;

    private static final int MAX_ATTEMPTS = 3;

    private final IdentityVerificationApplier identityVerificationApplier;

    public IdentityVerificationResult verify(long memberId, IdentityVerifyCommand command) {
        for (int attempt = 1; ; attempt++) {
            try {
                return identityVerificationApplier.apply(memberId, command);
            } catch (CannotAcquireLockException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("identity verification lock failure, retrying memberId={} attempt={}", memberId, attempt);
            }
        }
    }
}
