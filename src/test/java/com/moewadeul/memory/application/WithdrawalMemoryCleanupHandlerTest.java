package com.moewadeul.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.trust.application.withdrawal.WithdrawalEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WithdrawalMemoryCleanupHandlerTest {

    @Test
    @DisplayName("회원 탈퇴 시 trust가 기록하는 메모리 정리 이벤트 이름과 핸들러가 구독하는 이름이 같다")
    void eventTypeMatchesTrustProducer() {
        // given
        String producerEventType = WithdrawalEvents.MEMORY_CLEANUP_EVENT_TYPE;

        // when
        String consumerEventType = WithdrawalMemoryCleanupHandler.EVENT_TYPE;

        // then
        assertThat(consumerEventType).isEqualTo(producerEventType);
    }
}
