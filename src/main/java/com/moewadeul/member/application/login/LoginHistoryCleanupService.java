package com.moewadeul.member.application.login;

import com.moewadeul.member.infra.login.LoginAttemptLockMapper;
import com.moewadeul.member.infra.login.LoginHistoryCleanupMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 만든 지 3개월이 지난 로그인 기록과, 마지막 시도가 하루 넘게 지난 이메일별 잠금 행을 지운다. 이 서비스는 트랜잭션을 열지 않고
 * 삭제 문장마다 자동 커밋한다. 기록이 많이 쌓였을 때 전체를 한 트랜잭션으로 묶으면 오래 걸리는 동안 행 잠금을 쥐고 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryCleanupService {

    /** 로그인 기록을 보관하는 기간이다. 달력 기준 3개월이라 일수가 아니라 Period로 둔다. */
    static final Period RETENTION = Period.ofMonths(3);

    /** 잠금 행은 시도가 이어지는 동안만 쓴다. 마지막 시도 뒤 이만큼 지나면 쓸모가 없어서 지운다. */
    static final Duration LOCK_ROW_IDLE_LIMIT = Duration.ofHours(24);

    // 한 번에 지우는 행 수의 상한이다. 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final LoginHistoryCleanupMapper cleanupMapper;
    private final LoginAttemptLockMapper attemptLockMapper;
    private final Clock clock;

    /**
     * 호출하면 만든 시각이 지금(UTC)에서 3개월을 뺀 시각보다 먼저인 기록을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지
     * 되풀이하고, 지운 기록 수를 돌려준다. 정확히 3개월 된 기록은 남긴다. 잠금 행도 같은 방식으로 지우지만 돌려주는 수에는 세지 않는다.
     */
    public int deleteExpired() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.atZone(ZoneOffset.UTC).minus(RETENTION).toInstant();
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deleteOlderThan(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("login history deleted count={}", deletedTotal);
        deleteIdleLockRows(now.minus(LOCK_ROW_IDLE_LIMIT));
        return deletedTotal;
    }

    private void deleteIdleLockRows(Instant cutoff) {
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = attemptLockMapper.deleteIdleBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("login attempt lock rows deleted count={}", deletedTotal);
    }
}
