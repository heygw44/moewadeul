package com.moewadeul.common.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{12}";

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    @DisplayName("응답 헤더에 12자리 소문자 16진수 추적 ID를 담는다")
    void setsTraceIdHeaderOnResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {});

        assertThat(response.getHeader(TraceIdFilter.HEADER)).matches(TRACE_ID_PATTERN);
    }

    @Test
    @DisplayName("요청을 처리하는 동안 MDC에 응답 헤더와 같은 추적 ID가 있다")
    void exposesTraceIdInMdcWhileChainRuns() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> traceIdInChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(traceIdInChain.get()).isEqualTo(response.getHeader(TraceIdFilter.HEADER));
    }

    @Test
    @DisplayName("요청이 끝나면 MDC에서 추적 ID를 지운다")
    void removesTraceIdFromMdcAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("체인에서 예외가 나도 예외는 전파하고 MDC는 지운다")
    void removesTraceIdFromMdcWhenChainThrows() {
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() ->
                        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failingChain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("체인에서 예외가 나면 추적 ID가 MDC에 있는 채로 ERROR 로그를 남기고 예외를 다시 던진다")
    void logsFilterStageExceptionWithTraceId() {
        // given
        Logger filterLogger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("pool exhausted");
        };

        try {
            // when
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, failingChain))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            filterLogger.detachAppender(appender);
        }

        // then
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getMDCPropertyMap())
                    .containsEntry(TraceIdFilter.MDC_KEY, response.getHeader(TraceIdFilter.HEADER));
        });
    }

    @Test
    @DisplayName("클라이언트가 보낸 X-Trace-Id 헤더는 무시하고 새로 만든다")
    void ignoresInboundTraceIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "attacker-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> traceIdInChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.HEADER)).matches(TRACE_ID_PATTERN);
        assertThat(traceIdInChain.get()).isNotEqualTo("attacker-value");
    }

    @Test
    @DisplayName("요청마다 서로 다른 추적 ID를 만든다")
    void generatesDifferentTraceIdPerRequest() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> {});
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> {});

        assertThat(first.getHeader(TraceIdFilter.HEADER)).isNotEqualTo(second.getHeader(TraceIdFilter.HEADER));
    }
}
