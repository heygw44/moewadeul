package com.moewadeul.trust.application.sanction;

import com.moewadeul.member.application.MemberSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이 클래스에는 {@code @Transactional}을 두지 않는다. Spring Session JDBC는 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 커넥션을 기다리며 멈춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionConfirmService {

    private final SanctionConfirmApplier sanctionConfirmApplier;
    private final MemberSessions memberSessions;

    public SanctionConfirmResult confirm(SanctionConfirmCommand command) {
        SanctionConfirmResult result = sanctionConfirmApplier.apply(command);
        if (result.suspended()) {
            memberSessions.invalidateAll(result.memberId());
        }
        log.info(
                "sanction confirmed memberId={} sanctionId={} type={}",
                result.memberId(),
                result.sanctionId(),
                command.type());
        return result;
    }
}
