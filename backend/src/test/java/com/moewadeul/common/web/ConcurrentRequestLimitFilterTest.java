package com.moewadeul.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConcurrentRequestLimitFilterTest {

    private static final Duration LONG_WAIT = Duration.ofSeconds(30);

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("한도를 채운 뒤 들어온 요청은 기다리다가 앞선 요청이 끝나면 처리된다")
    void waitingRequestProceedsAfterOneFinishes() throws Exception {
        ConcurrentRequestLimitFilter filter = filter(2, LONG_WAIT);
        Blocker first = new Blocker();
        Blocker second = new Blocker();
        Blocker third = new Blocker();
        submit(filter, first);
        submit(filter, second);
        first.awaitEntered();
        second.awaitEntered();

        Future<?> waiter = submit(filter, third);

        await().untilAsserted(() ->
                assertThat(gauge("moewadeul.http.requests.waiting")).isEqualTo(1));
        assertThat(gauge("moewadeul.http.requests.in_flight")).isEqualTo(2);
        assertThat(third.entered.getCount()).isEqualTo(1);

        first.finish();
        third.awaitEntered();
        assertThat(gauge("moewadeul.http.requests.waiting")).isZero();
        assertThat(gauge("moewadeul.http.requests.in_flight")).isEqualTo(2);

        second.finish();
        third.finish();
        waiter.get(10, TimeUnit.SECONDS);
        await().untilAsserted(() ->
                assertThat(gauge("moewadeul.http.requests.in_flight")).isZero());
    }

    @Test
    @DisplayName("한도 안에서 자리를 얻지 못한 채 기다림이 끝나면 요청을 처리하지 않고 503 RESOURCE_BUSY로 답한다")
    void waiterGetsBusyResponseOnTimeout() throws Exception {
        ConcurrentRequestLimitFilter filter = filter(1, Duration.ofMillis(100));
        Blocker holder = new Blocker();
        submit(filter, holder);
        holder.awaitEntered();
        Blocker rejected = new Blocker();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, rejected);

        assertThat(response.getStatus()).isEqualTo(503);
        JsonNode body = JsonMapper.builder().build().readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("RESOURCE_BUSY");
        assertThat(rejected.entered.getCount()).isEqualTo(1);
        assertThat(gauge("moewadeul.http.requests.in_flight")).isEqualTo(1);
        assertThat(gauge("moewadeul.http.requests.waiting")).isZero();
        holder.finish();
    }

    @Test
    @DisplayName("요청 처리 중 예외가 나도 자리를 돌려준다")
    void releasesPermitWhenChainThrows() throws Exception {
        ConcurrentRequestLimitFilter filter = filter(1, LONG_WAIT);
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (IllegalStateException expected) {
            // 예외는 전파되고, 자리만 돌아오면 된다.
        }

        assertThat(gauge("moewadeul.http.requests.in_flight")).isZero();
    }

    @Test
    @DisplayName("비동기로 시작한 요청은 필터를 빠져나와도 끝날 때까지 자리를 쥐고, 끝나면 한 번만 돌려준다")
    void asyncRequestHoldsPermitUntilCompletion() throws Exception {
        ConcurrentRequestLimitFilter filter = filter(1, LONG_WAIT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        FilterChain startsAsync = (req, res) -> req.startAsync();

        filter.doFilter(request, new MockHttpServletResponse(), startsAsync);

        assertThat(gauge("moewadeul.http.requests.in_flight")).isEqualTo(1);

        request.getAsyncContext().complete();
        assertThat(gauge("moewadeul.http.requests.in_flight")).isZero();

        Blocker next = new Blocker();
        submit(filter, next);
        next.awaitEntered();
        request.getAsyncContext().complete();
        assertThat(gauge("moewadeul.http.requests.in_flight")).isEqualTo(1);
        next.finish();
    }

    @Test
    @DisplayName("가상 스레드를 켰을 때만 필터 빈이 생긴다")
    void beanExistsOnlyWithVirtualThreads() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(FilterConfig.class);

        runner.withPropertyValues("spring.threads.virtual.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ConcurrentRequestLimitFilter.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(ConcurrentRequestLimitFilter.class));
        runner.withPropertyValues("spring.threads.virtual.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ConcurrentRequestLimitFilter.class));
    }

    @Test
    @DisplayName("한도는 server.tomcat.threads.max를 따른다")
    void permitsFollowTomcatThreadsMax() {
        new ApplicationContextRunner()
                .withUserConfiguration(FilterConfig.class)
                .withPropertyValues("spring.threads.virtual.enabled=true", "server.tomcat.threads.max=3")
                .run(context -> {
                    ConcurrentRequestLimitFilter filter = context.getBean(ConcurrentRequestLimitFilter.class);
                    MeterRegistry meters = context.getBean(MeterRegistry.class);
                    List<Blocker> holders = List.of(new Blocker(), new Blocker(), new Blocker());
                    for (Blocker holder : holders) {
                        submit(filter, holder);
                        holder.awaitEntered();
                    }
                    assertThat(meters.get("moewadeul.http.requests.in_flight")
                                    .gauge()
                                    .value())
                            .isEqualTo(3);

                    submit(filter, new Blocker());
                    await().untilAsserted(() -> assertThat(meters.get("moewadeul.http.requests.waiting")
                                    .gauge()
                                    .value())
                            .isEqualTo(1));
                    holders.forEach(Blocker::finish);
                });
    }

    private ConcurrentRequestLimitFilter filter(int max, Duration timeout) {
        TomcatServerProperties properties = new TomcatServerProperties();
        properties.getThreads().setMax(max);
        return new ConcurrentRequestLimitFilter(properties, registry, timeout);
    }

    private Future<?> submit(ConcurrentRequestLimitFilter filter, Blocker chain) {
        return executor.submit(() -> {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
            return null;
        });
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private static final class Blocker implements FilterChain {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch finish = new CountDownLatch(1);

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            entered.countDown();
            try {
                finish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        }

        void finish() {
            finish.countDown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ConcurrentRequestLimitFilter.class)
    @EnableConfigurationProperties(TomcatServerProperties.class)
    static class FilterConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
