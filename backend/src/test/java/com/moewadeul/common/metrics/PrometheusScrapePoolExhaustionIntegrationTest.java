package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
@AutoConfigureMockMvc
class PrometheusScrapePoolExhaustionIntegrationTest {

    private static final long SCRAPE_DEADLINE_SECONDS = 5;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("[NFR-09] 커넥션 풀이 모두 쓰이는 중에도 /actuator/prometheus는 풀을 기다리지 않고 응답한다")
    void scrapeDoesNotWaitForConnectionPool() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        assertThat(pool.getConnectionTimeout()).isGreaterThan(TimeUnit.SECONDS.toMillis(SCRAPE_DEADLINE_SECONDS));
        List<Connection> held = new ArrayList<>();
        ExecutorService scraper = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < pool.getMaximumPoolSize(); i++) {
                held.add(pool.getConnection());
            }

            Future<String> scrape = scraper.submit(() -> mvc.get()
                    .uri("/actuator/prometheus")
                    .exchange()
                    .getResponse()
                    .getContentAsString());

            assertThat(scrape.get(SCRAPE_DEADLINE_SECONDS, TimeUnit.SECONDS)).contains("hikaricp_connections_active");
        } finally {
            scraper.shutdownNow();
            for (Connection connection : held) {
                closeQuietly(connection);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 풀로 돌려주지 못해도 테스트 결과는 바꾸지 않는다.
        }
    }
}
