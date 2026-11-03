package com.moewadeul.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";
    private static final int TRACE_ID_LENGTH = 12;

    // 비동기 요청은 컨테이너가 같은 요청을 다시 디스패치한다. 이때도 처음 만든 추적 ID를 써야 재디스패치에서 남기는 로그에 같은 ID가 찍힌다.
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = (String) request.getAttribute(ATTRIBUTE);
        if (traceId == null) {
            traceId = generateTraceId();
            request.setAttribute(ATTRIBUTE, traceId);
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | ServletException e) {
            // 컨테이너는 이 필터가 MDC를 지운 뒤에 예외를 처리한다. 필터 단계에서 난 예외는 추적 ID가 남아 있는 여기서 로그를 남긴다.
            log.error("unhandled exception in filter chain", e);
            throw e;
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
