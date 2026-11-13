package com.moewadeul.common.security;

import com.moewadeul.common.security.ThrottledSessionRepository.ThrottledSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;

// Boot의 JDBC 세션 설정은 SessionRepository 빈이 없을 때만 켜지므로, 저장소를 빈으로 두지 않고 필터만 바꿔 끼운다.
@Configuration(proxyBeanMethods = false)
public class ThrottledSessionConfig {

    @Bean
    static BeanPostProcessor throttledSessionFilterSwap(
            ObjectProvider<JdbcIndexedSessionRepository> jdbcRepository,
            ObjectProvider<HttpSessionIdResolver> sessionIdResolver,
            ObjectProvider<CookieSerializer> cookieSerializer) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof SessionRepositoryFilter<?>)) {
                    return bean;
                }
                SessionRepositoryFilter<?> swapped = throttled(jdbcRepository.getObject());
                swapped.setHttpSessionIdResolver(sessionIdResolver.getIfAvailable(() -> {
                    CookieHttpSessionIdResolver cookieResolver = new CookieHttpSessionIdResolver();
                    cookieResolver.setCookieSerializer(cookieSerializer.getObject());
                    return cookieResolver;
                }));
                return swapped;
            }
        };
    }

    private static <S extends Session> SessionRepositoryFilter<?> throttled(SessionRepository<S> jdbc) {
        return new RequestMemoSessionRepositoryFilter<>(new ThrottledSessionRepository<>(jdbc));
    }

    private static final class RequestMemoSessionRepositoryFilter<S extends Session>
            extends SessionRepositoryFilter<ThrottledSession<S>> {

        private final ThrottledSessionRepository<S> repository;

        private RequestMemoSessionRepositoryFilter(ThrottledSessionRepository<S> repository) {
            super(repository);
            this.repository = repository;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            repository.openRequestMemo();
            try {
                super.doFilterInternal(request, response, chain);
            } finally {
                repository.closeRequestMemo();
            }
        }
    }
}
