package com.moewadeul.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Spring Session은 컨트롤러가 끝날 때가 아니라 응답을 커밋할 때 세션을 저장한다.
// 이 필터는 세션 필터 바깥에 있어서 세션이 저장소에 들어간 뒤에 맡긴 작업을 실행한다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AfterSessionCommitFilter extends OncePerRequestFilter {

    private static final String TASK_ATTRIBUTE = AfterSessionCommitFilter.class.getName() + ".TASK";

    public static void register(HttpServletRequest request, Runnable task) {
        request.setAttribute(TASK_ATTRIBUTE, task);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Object task = request.getAttribute(TASK_ATTRIBUTE);
            if (task instanceof Runnable runnable) {
                runnable.run();
            }
        }
    }
}
