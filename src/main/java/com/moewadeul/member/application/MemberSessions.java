package com.moewadeul.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * DB 트랜잭션 안에서 부르지 않는다. Spring Session JDBC가 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션이 커넥션을 잡은 채 부르면 동시 요청이 풀 크기에 닿을 때 서로 기다리며 멈춘다.
 */
@Component
@RequiredArgsConstructor
public class MemberSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public void invalidateAll(long memberId) {
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).keySet().forEach(sessionRepository::deleteById);
    }

    public void invalidateAllExcept(long memberId, String keptSessionId) {
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).keySet().stream()
                .filter(sessionId -> !sessionId.equals(keptSessionId))
                .forEach(sessionRepository::deleteById);
    }

    public void invalidate(String sessionId) {
        sessionRepository.deleteById(sessionId);
    }
}
