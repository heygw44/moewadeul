package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.weather.infra.KmaClient;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WeatherQueryServiceTest {

    private final KmaClient kmaClient = mock(KmaClient.class);
    private final WeatherQueryService service = new WeatherQueryService(kmaClient, null, null, null, Clock.systemUTC());

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("기상청 예보를 트랜잭션 안에서 부르면 기상청에 묻기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.forecast(60, 127, 37.5, 127.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(kmaClient);
    }
}
