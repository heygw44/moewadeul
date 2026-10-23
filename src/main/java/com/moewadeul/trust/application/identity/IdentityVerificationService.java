package com.moewadeul.trust.application.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

/**
 * 회원이 본인확인을 한다. 저장은 {@link IdentityVerificationApplier}의 트랜잭션이 맡는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. 같은 CI로 여러 요청이 동시에 저장하면, MySQL이 유니크 인덱스의 중복 검사에서 잠금을
 * 기다리다 교착 상태를 감지하고 그중 한 트랜잭션을 롤백할 수 있다. 이 예외는 유니크 제약 위반이 아니라서 오류 코드로 바뀌지 않는다. 그래서 서비스는
 * 롤백된 트랜잭션 밖에서 최대 {@value #MAX_ATTEMPTS}번까지 실행한다. 다시 실행하면 먼저 저장된 행이 보이므로, 사전 조회가 중복을 알맞은 오류 코드로 거부한다.
 * 두 번만 시도하면 다시 실행한 요청이 또 교착 상태에 걸려 500으로 나가는 경우가 있었다(CI에서 10명 동시 요청 중 2건).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    /** 이 해보다 이른 출생연도는 입력 실수로 보고 거부한다. */
    static final int MIN_BIRTH_YEAR = 1900;

    private static final int MAX_ATTEMPTS = 3;

    private final IdentityVerificationApplier identityVerificationApplier;

    /**
     * 호출하면 memberId인 회원의 본인확인 기록을 저장하고 결과를 돌려준다.
     *
     * <p>출생연도가 1900년보다 이르거나 올해(한국 시각)보다 늦으면 INVALID_INPUT으로 거부한다. 이미 본인확인한 회원이면 IDENTITY_ALREADY_VERIFIED로,
     * 같은 CI로 본인확인한 다른 계정이 있으면 IDENTITY_CI_DUPLICATED로 거부한다. 두 경우가 겹치면 IDENTITY_ALREADY_VERIFIED가 먼저다.
     * 모든 시도에서 잠금을 얻지 못하면 마지막 예외를 그대로 던진다.
     */
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
