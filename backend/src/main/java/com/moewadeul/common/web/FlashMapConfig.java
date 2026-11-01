package com.moewadeul.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;

@Configuration(proxyBeanMethods = false)
public class FlashMapConfig {

    // Spring MVC의 기본 FlashMapManager는 리다이렉트 뒤에 넘길 값을 세션에 두므로, DispatcherServlet이 요청마다 세션을 연다.
    // 그래서 로그인한 회원이 보내는 요청은 지도 조회처럼 로그인 정보를 쓰지 않아도 세션 행을 읽고 마지막 접근 시각을 다시 쓴다.
    // 빈 이름이 flashMapManager여야 Spring Boot가 기본 관리자를 만들지 않고 DispatcherServlet이 이 빈을 쓴다.
    @Bean
    FlashMapManager flashMapManager() {
        return new NoSessionFlashMapManager();
    }

    private static final class NoSessionFlashMapManager implements FlashMapManager {

        @Override
        public FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) {
            return null;
        }

        @Override
        public void saveOutputFlashMap(FlashMap flashMap, HttpServletRequest request, HttpServletResponse response) {}
    }
}
