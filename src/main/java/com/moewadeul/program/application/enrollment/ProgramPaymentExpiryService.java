package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import com.moewadeul.program.infra.enrollment.ProgramPaymentExpiryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 기한이 지난 결제 대기 신청을 만료로 바꿔 자리를 돌려준다.
 *
 * <p>이 클래스는 트랜잭션을 열지 않는다. 대상을 나눠 읽은 뒤 신청마다 {@link ProgramPaymentExpiryApplier}를 불러
 * 신청 하나를 트랜잭션 하나로 처리한다. 한 신청이 실패해도 나머지는 계속 처리하고, 실패한 신청은 다음 실행에서 다시 집는다.
 * 실패한 신청의 수는 결과에 담아 돌려주므로, 호출하는 쪽이 그 수로 작업 실패 지표를 올린다.
 * 서버 여러 대가 동시에 돌려도 신청마다 조건부 UPDATE가 한 번만 성공하므로 중복 처리가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPaymentExpiryService {

    // 한 번에 읽는 신청의 수다. 밀린 신청이 많아도 목록이 너무 커지지 않게 나눈다.
    static final int BATCH_SIZE = 500;

    private final ProgramPaymentExpiryMapper expiryMapper;
    private final ProgramPaymentExpiryApplier applier;
    private final Clock clock;

    /**
     * 호출하면 이 시각 기준으로 결제 기한이 지난 결제 대기 신청을 모두 만료로 바꾸고, 실제로 바꾼 신청 수와 예외로 실패한 신청 수를 돌려준다.
     */
    public Result run() {
        Instant now = clock.instant();
        int expired = 0;
        int failed = 0;
        long afterId = 0;
        while (true) {
            List<ExpiryTarget> targets = expiryMapper.selectDueTargets(now, afterId, BATCH_SIZE);
            for (ExpiryTarget target : targets) {
                switch (expireOne(target, now)) {
                    case EXPIRED -> expired++;
                    case FAILED -> failed++;
                    case UNCHANGED -> {}
                }
            }
            if (targets.size() < BATCH_SIZE) {
                break;
            }
            afterId = targets.get(targets.size() - 1).applicationId();
        }
        log.info("program payment expiry completed expired={} failed={}", expired, failed);
        return new Result(expired, failed);
    }

    // 한 신청의 실패가 다른 신청의 처리를 막지 않게 여기서 잡는다. 실패한 신청은 상태가 그대로라 다음 실행의 대상이 된다.
    private ItemResult expireOne(ExpiryTarget target, Instant now) {
        try {
            return applier.expire(target, now) ? ItemResult.EXPIRED : ItemResult.UNCHANGED;
        } catch (RuntimeException e) {
            log.error("program payment expiry failed applicationId={}", target.applicationId(), e);
            return ItemResult.FAILED;
        }
    }

    // 바뀐 것이 없는 경우(결제나 취소가 먼저 처리함)와 예외로 실패한 경우를 가른다. 앞의 것은 정상이고 뒤의 것만 실패 지표에 올린다.
    private enum ItemResult {
        EXPIRED,
        UNCHANGED,
        FAILED
    }

    /**
     * @param expired 이번 실행에서 만료로 바꾼 신청 수
     * @param failed 이번 실행에서 예외로 처리하지 못한 신청 수. 상태가 그대로라 다음 실행에서 다시 처리한다
     */
    public record Result(int expired, int failed) {}
}
