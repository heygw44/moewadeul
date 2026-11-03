package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moewadeul.common.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class LocationWeatherServiceTransactionIntegrationTest {

    @Autowired
    private LocationWeatherService service;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WireMockServer wireMock;

    @Test
    @DisplayName("[F-09][NFR-05] 트랜잭션 안에서 부르면 IllegalStateException으로 거부하고 외부 API는 부르지 않는다")
    void rejectsCallInsideTransaction() {
        // when & then
        assertThatThrownBy(() ->
                        transactionTemplate.executeWithoutResult(status -> service.findWeather(60, 127, 37.5, 127.0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }
}
