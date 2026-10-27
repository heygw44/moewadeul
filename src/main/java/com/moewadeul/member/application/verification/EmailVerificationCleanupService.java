package com.moewadeul.member.application.verification;

import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.infra.verification.EmailVerificationCleanupMapper;
import com.moewadeul.member.infra.verification.EmailVerificationIpLockMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 발급한 지 7일이 지난 이메일 인증 기록과, 마지막 요청이 하루 넘게 지난 IP별 발송 잠금 행을 지운다. 이 서비스는 트랜잭션을 열지 않고
 * 삭제 문장마다 자동 커밋한다. 기록이 많이 쌓였을 때 전체를 한 트랜잭션으로 묶으면 오래 걸리는 동안 행 잠금을 쥐고 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationCleanupService {

    /** IP 잠금 행은 요청이 이어지는 동안만 쓴다. 마지막 요청 뒤 이만큼 지나면 쓸모가 없어서 지운다. */
    static final Duration IP_LOCK_ROW_IDLE_LIMIT = Duration.ofHours(24);

    // 한 번에 지우는 행 수의 상한이다. 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final EmailVerificationCleanupMapper cleanupMapper;
    private final EmailVerificationIpLockMapper ipLockMapper;
    private final Clock clock;

    /**
     * 호출하면 발급 시각이 지금에서 7일을 뺀 시각보다 먼저인 기록을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고,
     * 지운 행 수를 돌려준다. 정확히 7일 된 기록은 남긴다. IP 잠금 행도 같은 방식으로 지우지만 돌려주는 수에는 세지 않는다.
     */
    public int deleteExpired() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(EmailVerificationPolicy.RECORD_RETENTION);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deleteOlderThan(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("email verification deleted count={}", deletedTotal);
        deleteIdleIpLockRows(now.minus(IP_LOCK_ROW_IDLE_LIMIT));
        return deletedTotal;
    }

    private void deleteIdleIpLockRows(Instant cutoff) {
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = ipLockMapper.deleteIdleBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("email verification ip lock rows deleted count={}", deletedTotal);
    }
}
