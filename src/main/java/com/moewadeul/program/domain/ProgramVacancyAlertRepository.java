package com.moewadeul.program.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProgramVacancyAlertRepository {

    /**
     * 호출하면 memberId인 회원의 빈자리 알림 신청을 만든다. 이미 신청했으면 아무것도 바꾸지 않는다.
     * 새로 만들었으면 1, 이미 있었으면 0을 돌려준다.
     */
    int subscribe(long programId, long memberId, Instant now);

    /** 호출하면 memberId인 회원의 빈자리 알림 신청을 지운다. 신청한 적이 없으면 아무것도 하지 않는다. */
    void unsubscribe(long programId, long memberId);

    /**
     * 호출하면 programId인 행사의 알림 신청자 중 결제 대기·확정 신청이 없고, 아직 알림을 받지 않았거나 마지막 알림 시각이
     * notifiedBefore 이하인 회원의 ID를 알림을 먼저 신청한 순서로 돌려준다. 알림 쪽은 이 순서대로 빈자리 메일을 받을 회원을 정한다.
     */
    List<Long> findAlertTargets(long programId, Instant notifiedBefore);

    /**
     * 호출하면 findAlertTargets와 같은 회원을 같은 순서로 돌려주면서, 그 행사의 알림 신청 행을 트랜잭션이 끝날 때까지 쓰기 잠금으로 잡는다.
     * 다른 트랜잭션이 같은 행을 잡고 있으면 그 트랜잭션이 끝날 때까지 기다린 뒤 최신 알림 시각으로 거른다.
     */
    List<Long> lockAlertTargets(long programId, Instant notifiedBefore);

    /** 호출하면 programId인 행사에서 memberIds인 회원의 알림 시각을 now로 적는다. */
    void markNotified(long programId, Collection<Long> memberIds, Instant now);
}
