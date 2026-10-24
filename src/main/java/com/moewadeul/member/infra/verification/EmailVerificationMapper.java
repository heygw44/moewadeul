package com.moewadeul.member.infra.verification;

import com.moewadeul.common.outbox.OutboxEventStatus;
import com.moewadeul.member.domain.verification.SendWindow;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 시도 횟수와 인증 시각을 바꾸는 두 메서드는 조건부 UPDATE다. 같은 코드를 동시에 입력하거나 틀린 코드를 동시에 입력해도 결과가
// 어긋나지 않으려면, 읽은 값에 1을 더해 JPA로 저장하지 말고 DB가 조건을 검사하면서 한 번에 바꿔야 한다.
// 그래서 성공 여부를 영향받은 행 수로 판단하는 MyBatis를 쓴다.
// 아웃박스를 세는 조회는 이벤트 payload의 JSON 필드를 SQL로 골라야 해서 MyBatis를 쓴다.
// 이 매퍼의 모든 메서드는 호출하는 쪽(EmailVerificationService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface EmailVerificationMapper {

    /**
     * 호출하면 아직 인증하지 않았고 시도 횟수가 {@code maxAttempts} 미만인 행의 시도 횟수를 1 올리고 1을 돌려준다.
     * 이미 한도에 닿았거나 인증이 끝났으면 0을 돌려준다.
     */
    int incrementAttempt(@Param("id") long id, @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    /**
     * 호출하면 아직 인증하지 않았고 시도 횟수가 {@code maxAttempts} 미만인 행에 인증 시각을 기록하고 1을 돌려준다.
     * 동시에 같은 코드를 보낸 다른 요청이 먼저 이겼거나 한도에 닿았으면 0을 돌려준다.
     */
    int markVerified(@Param("id") long id, @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    /**
     * 호출하면 {@code since}보다 나중에 기록했고 아직 처리하지 않은({@code PENDING}) 발송 요청 가운데 그 IP가 요청했고 코드 행이 아직 없는 것의
     * 수와 가장 오래된 기록 시각을 돌려준다. 코드 행이 이미 있는 요청(메일 발송이 실패해서 다시 처리할 차례인 요청 등)은 코드 행 쪽에서 세므로
     * 여기서 빼서 두 번 세지 않는다. 대기 중인 요청이 없으면 횟수가 0이고 시각은 null이다.
     */
    SendWindow summarizeQueuedSendsByIpSince(
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("status") OutboxEventStatus status,
            @Param("requestIp") String requestIp,
            @Param("since") Instant since);
}
