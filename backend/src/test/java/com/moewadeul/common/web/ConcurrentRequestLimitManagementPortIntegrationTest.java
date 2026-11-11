package com.moewadeul.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.moewadeul.common.testsupport.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {"spring.threads.virtual.enabled=true", "server.tomcat.threads.max=1", "management.server.port=0"})
class ConcurrentRequestLimitManagementPortIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ConcurrentRequestLimitFilter filter;

    @Autowired
    private MeterRegistry registry;

    @Test
    @DisplayName("한도가 가득 차 요청 포트의 요청이 기다리는 동안에도 관리 포트는 한도를 거치지 않고 바로 답한다")
    void managementPortBypassesTheCap() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            try {
                filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    try {
                        finish.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<HttpResponse<String>> queued =
                http.sendAsync(request(port, "/actuator/health"), HttpResponse.BodyHandlers.ofString());
        await().untilAsserted(() -> assertThat(
                        registry.get("moewadeul.http.requests.waiting").gauge().value())
                .isEqualTo(1));

        HttpResponse<String> management =
                http.send(request(managementPort, "/actuator/health"), HttpResponse.BodyHandlers.ofString());

        assertThat(management.statusCode()).isEqualTo(200);
        assertThat(queued.isDone()).isFalse();

        finish.countDown();
        holder.get(10, TimeUnit.SECONDS);
        assertThat(queued.get(10, TimeUnit.SECONDS).statusCode()).isNotEqualTo(503);
    }

    private static HttpRequest request(int targetPort, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + path))
                .build();
    }
}
