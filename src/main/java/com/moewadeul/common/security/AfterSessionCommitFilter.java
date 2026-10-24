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

/**
 * 요청이 끝나고 세션을 저장소에 저장한 뒤에 컨트롤러가 맡겨 둔 작업을 실행한다.
 *
 * <p>Spring Session은 세션을 컨트롤러가 끝났을 때가 아니라 응답을 커밋할 때 저장한다. 그래서 컨트롤러 안에서는 방금 만든 세션이 아직
 * 저장소에 없다. 로그인 요청이 이 사이에 일어난 비밀번호 재설정이나 정지를 확인하려면, 세션이 저장소에 들어간 뒤에 확인해야 한다.
 * 그 확인이 세션을 저장한 뒤라면, 재설정·정지가 커밋 뒤 세션을 지우는 시점이 확인 앞이든 뒤든 한쪽은 이 세션을 반드시 본다.
 *
 * <p>이 필터는 세션 필터보다 바깥에 두어서, 안쪽 세션 필터가 세션을 저장하고 돌아온 뒤에 작업을 실행한다.
 * 응답은 이미 커밋됐을 수 있으므로 작업은 응답을 바꾸지 않고 저장소만 고친다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AfterSessionCommitFilter extends OncePerRequestFilter {

    private static final String TASK_ATTRIBUTE = AfterSessionCommitFilter.class.getName() + ".TASK";

    /** 호출하면 세션을 저장한 뒤 실행할 작업을 이 요청에 맡긴다. 요청마다 하나만 맡길 수 있고, 다시 맡기면 앞의 작업을 바꾼다. */
    public static void register(HttpServletRequest request, Runnable task) {
        request.setAttribute(TASK_ATTRIBUTE, task);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 컨트롤러가 작업을 맡긴 뒤 응답을 쓰다 실패해도 세션은 저장됐을 수 있다. 그래서 예외가 나도 작업을 실행한다.
            Object task = request.getAttribute(TASK_ATTRIBUTE);
            if (task instanceof Runnable runnable) {
                runnable.run();
            }
        }
    }
}
