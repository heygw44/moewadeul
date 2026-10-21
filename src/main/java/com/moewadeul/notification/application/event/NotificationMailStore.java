package com.moewadeul.notification.application.event;

import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.time.Instant;
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
     */
    @Transactional
    List<NotificationMailRow> claim(Instant now) {
        List<NotificationMailRow> rows = mailMapper.selectDueForUpdate(
                now, properties.batchSize(), NotificationEventTypes.DEFAULT_EMAIL_ENABLED);
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
                    targets.stream().map(NotificationMailRow::notificationId).toList(), now.plus(properties.lease()));
        }
        return targets;
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
}
