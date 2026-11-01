package com.moewadeul.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class ShutdownSignalTest {

    @Test
    @DisplayName("[NFR-04] 앱 컨텍스트를 닫기 시작하면 종료 신호가 켜진다")
    void isRequestedOnceApplicationContextCloses() {
        // given
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(ShutdownSignal.class);
        context.refresh();
        ShutdownSignal signal = context.getBean(ShutdownSignal.class);
        assertThat(signal.isRequested()).isFalse();

        // when
        context.close();

        // then
        assertThat(signal.isRequested()).isTrue();
    }
}
