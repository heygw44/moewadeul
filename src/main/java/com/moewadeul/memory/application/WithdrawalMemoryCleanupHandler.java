package com.moewadeul.memory.application;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 뒤에 오는 추억 기록 정리 이벤트를 받아, 탈퇴한 회원의 기록과 사진을 지운다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 트랜잭션은 {@link MemoryWithdrawalCleanupService}가 단계마다 따로 연다. 정리에
 * 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다.
 */
@Component
class WithdrawalMemoryCleanupHandler implements OutboxEventHandler {

    // 이벤트를 기록하는 trust 모듈의 상수와 같은 값이다. memory는 trust 모듈의 클래스를 가져다 쓰지 않으므로 문자열을 따로 둔다.
    static final String EVENT_TYPE = "WITHDRAWAL_MEMORY_CLEANUP";

    private final MemoryWithdrawalCleanupService cleanupService;

    WithdrawalMemoryCleanupHandler(MemoryWithdrawalCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    // 이벤트의 aggregate ID가 탈퇴한 회원 ID라서 payload는 읽지 않는다.
    @Override
    public void handle(OutboxMessage message) {
        cleanupService.cleanUp(message.aggregateId());
    }
}
