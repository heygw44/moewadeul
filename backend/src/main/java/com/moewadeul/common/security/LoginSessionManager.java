package com.moewadeul.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    // null(비밀번호를 바꾼 적 없음)과 속성이 없는 세션을 구분하려고 문자열로 적는다.
    public static final String PASSWORD_CHANGED_AT_ATTRIBUTE =
            LoginSessionManager.class.getName() + ".PASSWORD_CHANGED_AT";

    private static final String ROLE_PREFIX = "ROLE_";

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixationStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public LoginSessionManager(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public static String passwordChangedAtSnapshot(Instant passwordChangedAt) {
        return String.valueOf(passwordChangedAt);
    }

    public void login(
            LoginMember member, Instant passwordChangedAt, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationOf(member);

        sessionFixationStrategy.onAuthentication(authentication, request, response);

        storeContext(authentication, request, response);
        recordPasswordChangedAt(request, passwordChangedAt);
    }

    public void recordPasswordChangedAt(HttpServletRequest request, Instant passwordChangedAt) {
        request.getSession().setAttribute(PASSWORD_CHANGED_AT_ATTRIBUTE, passwordChangedAtSnapshot(passwordChangedAt));
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
