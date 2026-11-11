package com.moewadeul.common.web;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.ErrorResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.thread.Threading;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

// 가상 스레드에서는 Tomcat 스레드 수가 동시 요청 수를 막지 못해서, 같은 값을 동시 처리 한도로 다시 건다.
// 세션 필터와 보안 필터보다 앞에 두어 기다리는 요청이 영속성 컨텍스트나 커넥션을 쥐지 않게 한다.
// 추적 ID 필터는 그보다도 앞에 두어 503 응답에도 추적 ID가 실리게 한다.
@Component
@ConditionalOnThreading(Threading.VIRTUAL)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ConcurrentRequestLimitFilter extends OncePerRequestFilter {

    static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(60);

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String IN_FLIGHT_GAUGE_NAME = "moewadeul.http.requests.in_flight";
    private static final String WAITING_GAUGE_NAME = "moewadeul.http.requests.waiting";

    private final int permitCount;
    private final Semaphore permits;
    private final Duration acquireTimeout;

    @Autowired
    public ConcurrentRequestLimitFilter(TomcatServerProperties tomcatProperties, MeterRegistry registry) {
        this(tomcatProperties, registry, ACQUIRE_TIMEOUT);
    }

    ConcurrentRequestLimitFilter(
            TomcatServerProperties tomcatProperties, MeterRegistry registry, Duration acquireTimeout) {
        this.permitCount = tomcatProperties.getThreads().getMax();
        this.permits = new Semaphore(permitCount, true);
        this.acquireTimeout = acquireTimeout;
        Gauge.builder(IN_FLIGHT_GAUGE_NAME, this, filter -> filter.permitCount - filter.permits.availablePermits())
                .description("한도 안에서 처리 중인 HTTP 요청 수")
                .register(registry);
        Gauge.builder(WAITING_GAUGE_NAME, permits, Semaphore::getQueueLength)
                .description("동시 처리 한도 때문에 자리를 기다리는 HTTP 요청 수")
                .register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!acquire()) {
            writeBusy(response);
            return;
        }
        AtomicBoolean released = new AtomicBoolean();
        try {
            chain.doFilter(request, response);
        } catch (Throwable e) {
            release(released);
            throw e;
        }
        if (request.isAsyncStarted()) {
            request.getAsyncContext().addListener(new ReleaseOnAsyncEnd(released));
        } else {
            release(released);
        }
    }

    private boolean acquire() {
        try {
            return permits.tryAcquire(acquireTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void release(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) {
            permits.release();
        }
    }

    private static void writeBusy(HttpServletResponse response) throws IOException {
        response.setStatus(CommonErrorCode.RESOURCE_BUSY.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream()
                .write(JSON_MAPPER.writeValueAsBytes(ErrorResponse.of(CommonErrorCode.RESOURCE_BUSY)));
    }

    // 비동기 시작 요청은 필터를 빠져나온 뒤에도 일하므로 끝날 때까지 자리를 쥔다. 비동기 재디스패치는 이 필터가 건너뛴다.
    private final class ReleaseOnAsyncEnd implements AsyncListener {

        private final AtomicBoolean released;

        private ReleaseOnAsyncEnd(AtomicBoolean released) {
            this.released = released;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            release(released);
        }

        @Override
        public void onTimeout(AsyncEvent event) {}

        @Override
        public void onError(AsyncEvent event) {}

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
