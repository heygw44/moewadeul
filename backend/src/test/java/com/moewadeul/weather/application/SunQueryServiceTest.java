package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.weather.infra.KasiClient;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SunQueryServiceTest {

    private final KasiClient kasiClient = mock(KasiClient.class);
    private final SunQueryService service = new SunQueryService(kasiClient, null, null);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("일출·일몰 시각을 트랜잭션 안에서 부르면 천문연에 묻기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.sunTimes(37.5, 127.0, LocalDate.of(2026, 10, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(kasiClient);
    }
}
