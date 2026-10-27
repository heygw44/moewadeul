package com.moewadeul.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN_ROLE = "ADMIN";

    private static final String[] PUBLIC_GET_PATTERNS = {
        "/api/members/*/profile",
        "/api/spots",
        "/api/spots/nearby",
        "/api/spots/*",
        "/api/spots/*/reviews",
        "/api/spots/*/weather",
        "/api/basecamps",
        "/api/basecamps/*",
        "/api/programs",
        "/api/programs/*",
        "/api/community/posts",
        "/api/community/posts/*",
        "/api/community/posts/*/comments",
        "/api/memories/feed",
        "/api/memories/*",
        "/api/members/*/memories",
    };

    private static final String[] PUBLIC_POST_PATTERNS = {
        "/api/members", "/api/auth/login", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm",
    };

    private static final String[] VERIFIED_GET_PATTERNS = {
        "/api/members/*/companion-reviews", "/api/me/companion-reviews/pending", "/api/basecamps/*/applications",
    };

    private static final String[] VERIFIED_POST_PATTERNS = {
        "/api/me/identity-verification",
        "/api/bakjis",
        "/api/spots/ai-interpret",
        "/api/bakjis/*/confirmations",
        "/api/bakjis/*/reports",
        "/api/spots/*/reviews",
        "/api/reviews/images",
        "/api/community/posts",
        "/api/community/images",
        "/api/community/posts/*/comments",
        "/api/community/posts/*/reports",
        "/api/community/comments/*/reports",
        "/api/basecamps",
        "/api/basecamps/*/close",
        "/api/basecamps/*/reopen",
        "/api/basecamps/*/confirm",
        "/api/basecamps/*/cancel",
        "/api/basecamps/*/applications",
        "/api/basecamps/*/applications/*/approve",
        "/api/basecamps/*/applications/*/reject",
        "/api/basecamps/*/members/*/kick",
        "/api/basecamps/*/companion-reviews",
        "/api/member-reports",
        "/api/programs/*/applications",
        "/api/programs/*/vacancy-alerts",
        "/api/program-applications/*/pay",
        "/api/program-applications/*/cancel",
        "/api/solo-trips",
        "/api/memories",
        "/api/memories/images",
        "/api/memories/*/reports",
    };

    private static final String[] VERIFIED_PUT_PATTERNS = {
        "/api/basecamps/*/contact", "/api/community/posts/*/like", "/api/memories/*/like",
    };

    private static final String[] VERIFIED_PATCH_PATTERNS = {
        "/api/me",
        "/api/bakjis/*",
        "/api/reviews/*",
        "/api/basecamps/*",
        "/api/community/posts/*",
        "/api/community/comments/*",
        "/api/memories/*",
    };

    private static final String[] VERIFIED_DELETE_PATTERNS = {
        "/api/bakjis/*",
        "/api/reviews/*",
        "/api/community/posts/*",
        "/api/community/comments/*",
        "/api/community/posts/*/like",
        "/api/memories/*/like",
        "/api/basecamps/*/applications/me",
        "/api/basecamps/*/members/me",
        "/api/programs/*/vacancy-alerts",
        "/api/solo-trips/*",
        "/api/memories/*",
    };

    private static final AuthorizationManager<RequestAuthorizationContext> EMAIL_VERIFIED =
            new EmailVerifiedAuthorizationManager();

    private static final String[] INFRASTRUCTURE_PATTERNS = {
        "/actuator/health", "/actuator/prometheus", "/swagger-ui/**", "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        SecurityErrorResponseHandler errorResponseHandler = new SecurityErrorResponseHandler();
        http.authorizeHttpRequests(SecurityConfig::authorizeRequests)
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorResponseHandler)
                        .accessDeniedHandler(errorResponseHandler));
        return http.build();
    }

    private static void authorizeRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requests) {
        requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATTERNS)
                .permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATTERNS)
                .permitAll()
                .requestMatchers(INFRASTRUCTURE_PATTERNS)
                .permitAll()
                .requestMatchers(HttpMethod.GET, VERIFIED_GET_PATTERNS)
                .access(EMAIL_VERIFIED)
                .requestMatchers(HttpMethod.POST, VERIFIED_POST_PATTERNS)
                .access(EMAIL_VERIFIED)
                .requestMatchers(HttpMethod.PUT, VERIFIED_PUT_PATTERNS)
                .access(EMAIL_VERIFIED)
                .requestMatchers(HttpMethod.PATCH, VERIFIED_PATCH_PATTERNS)
                .access(EMAIL_VERIFIED)
                .requestMatchers(HttpMethod.DELETE, VERIFIED_DELETE_PATTERNS)
                .access(EMAIL_VERIFIED)
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .denyAll();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // 인증 매니저나 사용자 조회 빈이 하나도 없으면 Spring Boot가 임시 비밀번호를 가진 사용자를 만들고 로그에 비밀번호를 남긴다.
    // 이 빈을 두면 Boot가 그 사용자를 만들지 않는다. 이 매니저는 어떤 인증 요청도 받지 않는다.
    @Bean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new ProviderNotFoundException("이 서버는 Spring Security 인증 매니저로 인증하지 않는다.");
        };
    }
}
