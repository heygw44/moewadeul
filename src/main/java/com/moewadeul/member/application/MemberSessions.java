package com.moewadeul.member.application;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberSessions {

    private static final String SESSION_CONNECTION =
            "Spring Session JDBC는 세션 작업마다 커넥션을 하나 더 잡아서, 트랜잭션 안에서 부르면 커넥션 풀이 바닥날 때 서로 기다리며 멈춘다.";

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public void invalidateAll(long memberId) {
        OutsideTransaction.require(SESSION_CONNECTION);
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).keySet().forEach(sessionRepository::deleteById);
    }

    public void invalidateAllExcept(long memberId, String keptSessionId) {
        OutsideTransaction.require(SESSION_CONNECTION);
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).keySet().stream()
                .filter(sessionId -> !sessionId.equals(keptSessionId))
                .forEach(sessionRepository::deleteById);
    }

    public void invalidateNotLoggedInWith(long memberId, Instant passwordChangedAt) {
        OutsideTransaction.require(SESSION_CONNECTION);
        String current = LoginSessionManager.passwordChangedAtSnapshot(passwordChangedAt);
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).entrySet().stream()
                .filter(entry -> !current.equals(
                        entry.getValue().getAttribute(LoginSessionManager.PASSWORD_CHANGED_AT_ATTRIBUTE)))
                .forEach(entry -> sessionRepository.deleteById(entry.getKey()));
    }

    public void invalidate(String sessionId) {
        OutsideTransaction.require(SESSION_CONNECTION);
        sessionRepository.deleteById(sessionId);
    }

    public void markEmailVerified(long memberId) {
        OutsideTransaction.require(SESSION_CONNECTION);
        markEmailVerified(sessionRepository, memberId);
    }

    private static <S extends Session> void markEmailVerified(
            FindByIndexNameSessionRepository<S> repository, long memberId) {
        repository.findByPrincipalName(String.valueOf(memberId)).values().forEach(session -> {
            if (session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                            instanceof SecurityContext context
                    && context.getAuthentication() != null
                    && context.getAuthentication().getPrincipal() instanceof LoginMember member
                    && !member.emailVerified()) {
                LoginMember verified = new LoginMember(member.memberId(), member.role(), true);
                session.setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        new SecurityContextImpl(LoginSessionManager.authenticationOf(verified)));
                repository.save(session);
            }
        });
    }
}
