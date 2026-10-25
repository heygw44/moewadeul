package com.moewadeul.notification.application.event;

import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 메일을 보내는 NotificationMailer는 메일 서버 응답을 기다리는 동안 DB 커넥션과 행 잠금을 잡으면 안 된다.
// 그래서 메일러의 DB 접근은 모두 이 클래스의 짧은 트랜잭션 메서드로만 한다.
@Slf4j
@Service
@RequiredArgsConstructor
class NotificationMailStore {

    private final NotificationMailMapper mailMapper;
    private final NotificationMailProperties properties;
    private final Clock clock;

    /**
     * 호출하면 지금 보낼 차례인 PENDING 알림을 한 트랜잭션에서 집고, 보낼 수 있는 알림에는 임대를 걸어 알림 ID 순으로 돌려준다.
     * 받는 회원이 그 종류의 이메일을 껐거나 메일을 받을 수 없으면 SKIPPED로 바꾸고 돌려주지 않는다. 임대는 now에서 {@code lease}
     * 설정 시간이 지날 때까지 다른 발송 작업이 같은 알림을 집지 못하게 막는다.
     *
     * <p>최근 24시간에 보낸 알림 메일이 상한({@link NotificationEventTypes#EMAIL_LIMIT_PER_WINDOW})에 닿았으면 아무것도 집지 않는다.
     * 남은 자리가 한 번에 집는 수보다 적으면 남은 자리만큼만 집는다. 집지 않은 알림은 PENDING 그대로 알림 ID 순서를 지키며 기다린다.
     * 서버가 한 대면 상한을 넘지 않는다. 발송 작업이 여러 곳에서 동시에 집으면 남은 자리를 같이 읽어서, 동시에 집는 발송 작업 수만큼
     * 한 번에 집는 수(batchSize)까지 상한을 넘길 수 있다.
     */
    @Transactional
    Claim claim(Instant now) {
        // 컬럼이 마이크로초까지만 저장하므로, 나노초가 붙은 시각은 저장한 값과 같은지 비교할 때 어긋날 수 있다.
        Instant leaseUntil = now.plus(properties.lease()).truncatedTo(ChronoUnit.MICROS);
        int remaining = remainingQuota(now);
        if (remaining == 0) {
            return new Claim(leaseUntil, List.of());
        }
        List<NotificationMailRow> rows = mailMapper.selectDueForUpdate(
                now, Math.min(properties.batchSize(), remaining), NotificationEventTypes.DEFAULT_EMAIL_ENABLED);
        List<Long> skipped = rows.stream()
                .filter(row -> !row.deliverable())
                .map(NotificationMailRow::notificationId)
                .toList();
        List<NotificationMailRow> targets =
                rows.stream().filter(NotificationMailRow::deliverable).toList();
        if (!skipped.isEmpty()) {
            mailMapper.markSkipped(skipped);
        }
        if (!targets.isEmpty()) {
            mailMapper.lease(
                    targets.stream().map(NotificationMailRow::notificationId).toList(), leaseUntil);
        }
        return new Claim(leaseUntil, targets);
    }

    // 보낸 메일은 SENT 알림의 보낸 시각으로 센다. 집어서 보내는 중인 메일은 아직 세지 않는다.
    private int remainingQuota(Instant now) {
        int sentInWindow = mailMapper.countSentAfter(now.minus(NotificationEventTypes.EMAIL_LIMIT_WINDOW));
        return Math.max(0, NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW - sentInWindow);
    }

    /**
     * 호출하면 집었지만 보내지 않은 알림의 임대를 풀어, 다른 발송 작업이 바로 집게 하고 푼 행 수를 돌려준다. 다음 시도 시각이
     * {@code leaseUntil} 그대로인 알림만 푼다. 보내는 데 임대 시간보다 오래 걸려 다른 발송 작업이 이미 다시 집은 알림은 그 작업의
     * 임대를 지켜야 하기 때문이다.
     */
    @Transactional
    int releaseLease(Instant leaseUntil, List<Long> notificationIds) {
        return mailMapper.releaseLease(notificationIds, leaseUntil);
    }

    /** 호출하면 그 알림의 메일 상태를 SENT로 바꾸고 보낸 시각을 지금으로 기록한다. */
    @Transactional
    void markSent(long notificationId) {
        if (mailMapper.markSent(notificationId, clock.instant()) == 0) {
            // 집은 알림을 임대 시간보다 오래 보내면 다른 발송 작업이 같은 알림을 다시 집어 먼저 끝냈을 수 있다.
            // 이 경우 같은 메일이 두 번 나갔으므로, 사람이 확인할 수 있게 남긴다.
            log.warn("notification mail was not pending when marking sent notificationId={}", notificationId);
        }
    }

    /**
     * 호출하면 그 알림의 실패 횟수를 올리고, 한도에 닿았으면 FAILED로, 아니면 재시도 시각까지 집지 않게 둔다. transientFailure가
     * true이면 한도에 닿아도 FAILED로 두지 않는다.
     */
    @Transactional
    void markFailedAttempt(long notificationId, boolean transientFailure) {
        int updated = mailMapper.markFailedAttempt(
                notificationId,
                transientFailure,
                properties.maxAttempts(),
                properties.retryDelay().toMillis(),
                properties.retryMaxDelay().toMillis(),
                clock.instant());
        if (updated == 0) {
            log.warn("notification mail was not pending when marking failed notificationId={}", notificationId);
        }
    }

    /**
     * 한 번 집어서 보낼 수 있는 알림과, 그 임대가 끝나는 시각이다.
     *
     * @param leaseUntil 집은 발송 작업이 건 임대 끝 시각. 보내지 못한 알림의 임대를 풀 때 자기 임대인지 알아보는 데 쓴다
     */
    record Claim(Instant leaseUntil, List<NotificationMailRow> targets) {}
}
