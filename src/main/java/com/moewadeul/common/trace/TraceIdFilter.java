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

    private static final int TRACE_ID_LENGTH = 12;

    // 클라이언트가 보낸 X-Trace-Id는 로그 주입 공격에 쓰일 수 있다. 그래서 서버는 이 값을 믿지 않고 항상 새로 만든다.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = generateTraceId();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | ServletException e) {
            // 컨트롤러에서 난 예외는 공통 예외 처리기가 이미 로그로 남긴다. 여기까지 올라온 예외는 세션 읽기가 커넥션 풀 고갈로 실패한 것처럼
            // 필터 단계에서 난 것이다. 컨테이너는 이 필터가 MDC를 지운 뒤에 예외를 처리하므로, 추적 ID가 남아 있는 지금 로그를 남기고 다시 던진다.
            // 클라이언트가 연결을 끊어서 생기는 IOException은 오류가 아니라서 남기지 않는다.
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
