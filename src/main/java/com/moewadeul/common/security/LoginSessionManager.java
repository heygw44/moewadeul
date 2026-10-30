package com.moewadeul.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class LoginSessionManager {

    // ChangeSessionIdAuthenticationStrategy는 로그인해도 세션 생성 시각을 그대로 두어서, 로그인 시각은 따로 적는다.
    public static final String LOGIN_INSTANT_ATTRIBUTE = LoginSessionManager.class.getName() + ".LOGIN_INSTANT";

    private static final String ROLE_PREFIX = "ROLE_";

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixationStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final Clock clock;

    public LoginSessionManager(SecurityContextRepository securityContextRepository, Clock clock) {
        this.securityContextRepository = securityContextRepository;
        this.clock = clock;
    }

    public void login(LoginMember member, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationOf(member);

        sessionFixationStrategy.onAuthentication(authentication, request, response);

        storeContext(authentication, request, response);
        stampLoginInstant(request);
    }

    public void stampLoginInstant(HttpServletRequest request) {
        request.getSession().setAttribute(LOGIN_INSTANT_ATTRIBUTE, Instant.now(clock));
    }

    public void markEmailVerified(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = securityContextHolderStrategy.getContext().getAuthentication();
        if (current == null || !(current.getPrincipal() instanceof LoginMember member)) {
            throw new IllegalStateException("로그인한 회원이 없는 요청에서 이메일 인증 표시를 요청했습니다.");
        }
        if (member.emailVerified()) {
            return;
        }
        LoginMember verified = new LoginMember(member.memberId(), member.role(), true);
        storeContext(authenticationOf(verified), request, response);
    }

    public void changeSessionId(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            throw new IllegalStateException("세션이 없는 요청에서 세션 ID 재발급을 요청했습니다.");
        }
        request.changeSessionId();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(
                request, response, securityContextHolderStrategy.getContext().getAuthentication());
    }

    private static Authentication authenticationOf(LoginMember member) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + member.role()));
        if (member.emailVerified()) {
            authorities.add(new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED));
        }
        return UsernamePasswordAuthenticationToken.authenticated(member, null, authorities);
    }

    private void storeContext(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
