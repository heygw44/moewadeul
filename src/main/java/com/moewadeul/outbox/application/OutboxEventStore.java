package com.moewadeul.outbox.application;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.outbox.infra.OutboxEventMapper;
import com.moewadeul.outbox.infra.OutboxMessageRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 발행기는 처리기를 호출하는 동안 DB 트랜잭션을 열어 두면 안 된다. 그래서 발행기의 DB 접근은
// 모두 이 클래스의 짧은 트랜잭션 메서드로만 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventStore {

    private final OutboxEventMapper mapper;
    private final OutboxProperties properties;
    private final Clock clock;

    /**
     * 호출하면 발행할 이벤트를 한 트랜잭션에서 고르고 임대를 건 뒤 돌려준다. 임대는 now에서 {@code lease} 설정 시간이 지날 때까지
     * 다른 발행기가 같은 이벤트를 집지 못하게 막는다.
     */
    @Transactional
    public Claim claim(Instant now) {
        List<Long> ids = mapper.selectClaimableIds(now, properties.batchSize());
        // 컬럼이 마이크로초까지만 저장하므로, 나노초가 붙은 시각은 저장한 값과 같은지 비교할 때 어긋날 수 있다.
        Instant leaseUntil = now.plus(properties.lease()).truncatedTo(ChronoUnit.MICROS);
        if (ids.isEmpty()) {
            return new Claim(leaseUntil, List.of());
        }
        mapper.lease(ids, leaseUntil, now);
        return new Claim(
                leaseUntil,
                mapper.selectByIds(ids).stream()
                        .map(OutboxEventStore::toMessage)
                        .toList());
    }

    /**
     * 호출하면 집었지만 처리하지 않은 이벤트의 임대를 풀어, 다른 발행기가 바로 집게 하고 푼 행 수를 돌려준다. 임대 만료 시각이
     * {@code leaseUntil} 그대로인 행만 푼다. 처리가 임대 시간보다 길어져 다른 발행기가 이미 다시 집은 이벤트는 그 발행기의 임대를
     * 지켜야 하기 때문이다.
     */
    @Transactional
    public int releaseLease(Instant leaseUntil, List<Long> eventIds) {
        return mapper.releaseLease(eventIds, leaseUntil, clock.instant());
    }

    @Transactional
    public void markPublished(long eventId) {
        int updated = mapper.markPublished(eventId, clock.instant());
        if (updated == 0) {
            // 처리기가 임대 시간보다 오래 걸리면 다른 발행기가 같은 이벤트를 다시 집어 먼저 끝냈을 수 있다.
            // 이 경우 처리기가 같은 이벤트를 두 번 받았으므로, 사람이 확인할 수 있게 남긴다.
            log.warn("outbox event was not pending when marking published eventId={}", eventId);
        }
    }

    /**
     * 호출하면 이벤트의 실패를 기록하고 다음 재시도 시각을 정한다. transientFailure가 true이면 횟수가 한도를 넘어도 FAILED로 두지 않고
     * 계속 다시 시도한다.
     */
    @Transactional
    public void markFailedAttempt(long eventId, String error, boolean transientFailure) {
        int updated = mapper.markFailedAttempt(
                eventId,
                error,
                transientFailure,
                properties.maxAttempts(),
                properties.retryDelay().toMillis(),
                properties.retryMaxDelay().toMillis(),
                clock.instant());
        if (updated == 0) {
            log.warn("outbox event was not pending when marking failed eventId={}", eventId);
        }
    }

    private static OutboxMessage toMessage(OutboxMessageRow row) {
        return new OutboxMessage(
                row.id(), row.eventType(), row.aggregateType(), row.aggregateId(), row.payload(), row.createdAt());
    }

    /**
     * 한 번 집은 이벤트와, 그 임대가 끝나는 시각이다.
     *
     * @param leaseUntil 집은 발행기가 건 임대 만료 시각. 처리하지 못한 이벤트의 임대를 풀 때 자기 임대인지 알아보는 데 쓴다
     */
    public record Claim(Instant leaseUntil, List<OutboxMessage> messages) {}
}
