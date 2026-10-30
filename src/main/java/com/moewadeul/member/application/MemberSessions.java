package com.moewadeul.member.application;

import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
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

    public void invalidateLoggedInBefore(long memberId, Instant cutoff) {
        OutsideTransaction.require(SESSION_CONNECTION);
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).entrySet().stream()
                .filter(entry -> !isLoggedInAtOrAfter(entry.getValue(), cutoff))
                .forEach(entry -> sessionRepository.deleteById(entry.getKey()));
    }

    private static boolean isLoggedInAtOrAfter(Session session, Instant cutoff) {
        Object loggedInAt = session.getAttribute(LoginSessionManager.LOGIN_INSTANT_ATTRIBUTE);
        return loggedInAt instanceof Instant instant && !instant.isBefore(cutoff);
    }

    public void invalidate(String sessionId) {
        OutsideTransaction.require(SESSION_CONNECTION);
        sessionRepository.deleteById(sessionId);
    }
}
