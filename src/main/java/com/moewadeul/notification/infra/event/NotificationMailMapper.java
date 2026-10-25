package com.moewadeul.notification.infra.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 받는 사람의 주소와 회원 상태는 member 테이블에 있고, notification 모듈은 member 모듈의 Java 클래스를 쓸 수 없다.
// 그래서 알림, 이메일 수신 설정, 회원을 한 번에 조인하는 SQL로 보낼 대상을 고른다.
// 메일 상태는 여러 서버의 발송 작업이 함께 바꾸므로, 상태를 바꾸는 문장은 모두 지금 상태를 조건으로 거는 조건부 UPDATE다.
@Mapper
public interface NotificationMailMapper {

    /**
     * 호출하면 메일 상태가 PENDING이고 다음 시도 시각이 비었거나 now 이하인 알림을 알림 ID 순으로 최대 limit건 쓰기 잠금으로 읽는다.
     * 다른 발송 작업이 잠근 알림은 건너뛴다. 트랜잭션 안에서 불러야 잠금이 유지된다.
     *
     * <p>{@code deliverable}은 받는 회원이 그 종류의 이메일을 받기로 했고(설정이 없으면 defaultEmailEnabled), 상태가 ACTIVE나
     * SUSPENDED이고, 이메일이 있고 인증을 마쳤는지다. 인증을 마치지 않은 회원은 주소를 확인하지 않았고, 탈퇴 회원은 이메일이 지워져 있다.
     */
    List<NotificationMailRow> selectDueForUpdate(
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("defaultEmailEnabled") boolean defaultEmailEnabled);

    /** 호출하면 PENDING인 그 알림들의 다음 시도 시각을 leaseUntil로 미뤄, 다른 발송 작업이 그때까지 집지 못하게 한다. */
    int lease(@Param("ids") Collection<Long> ids, @Param("leaseUntil") Instant leaseUntil);

    /**
     * 호출하면 PENDING인 그 알림들 가운데 다음 시도 시각이 leaseUntil과 같은 알림의 다음 시도 시각을 비우고 갱신된 행 수를 돌려준다.
     * 시각이 다르면 다른 발송 작업이 그 알림을 다시 집은 것이라서 건드리지 않는다.
     */
    int releaseLease(@Param("ids") Collection<Long> ids, @Param("leaseUntil") Instant leaseUntil);

    /** 호출하면 PENDING인 그 알림들을 SKIPPED로 바꾼다. */
    int markSkipped(@Param("ids") Collection<Long> ids);

    /** 호출하면 PENDING인 그 알림을 SENT로 바꾸고 보낸 시각을 기록한다. 이미 다른 상태면 0이다. */
    int markSent(@Param("id") long id, @Param("now") Instant now);

    /**
     * 호출하면 PENDING인 그 알림의 실패 횟수를 1 올린다. 일시적인 실패가 아니고 올린 횟수가 maxAttempts에 닿으면 FAILED로 바꾼다.
     * 아니면 now에서 retryDelayMillis × 2^(올리기 전 실패 횟수) 뒤까지(retryMaxDelayMillis를 넘기지 않음) 집지 않게 한다.
     * 이미 다른 상태면 0이다.
     */
    int markFailedAttempt(
            @Param("id") long id,
            @Param("transientFailure") boolean transientFailure,
            @Param("maxAttempts") int maxAttempts,
            @Param("retryDelayMillis") long retryDelayMillis,
            @Param("retryMaxDelayMillis") long retryMaxDelayMillis,
            @Param("now") Instant now);

    /**
     * 호출하면 memberIds 가운데 type 종류의 메일을 지금 보낼 수 있는 회원의 ID를 읽는다. 조건은 {@link #selectDueForUpdate}의
     * deliverable과 같다. memberIds가 비어 있으면 호출하지 않는다.
     */
    List<Long> selectDeliverableMemberIds(
            @Param("type") String type,
            @Param("memberIds") Collection<Long> memberIds,
            @Param("defaultEmailEnabled") boolean defaultEmailEnabled);

    /**
     * 호출하면 since보다 늦게 보낸 알림 메일 수를 센다. 모든 알림 종류를 합친다. 보낸 시각은 SENT 알림에만 있어서, 다른 상태의 알림은
     * 세지 않는다.
     */
    int countSentAfter(@Param("since") Instant since);

    /** 호출하면 PENDING·FAILED 알림 수와 가장 오래된 PENDING 알림의 생성 시각을 읽는다. */
    NotificationMailBacklogRow selectBacklog();
}
