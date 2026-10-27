package com.moewadeul.basecamp.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 베이스캠프 쓰기 중에는 같은 회원의 요청을 한 줄로 세워야 하는 것이 있다. 예를 들어 같은 회원이 베이스캠프 열기를 동시에 보내면 두 요청이
// 모두 "아직 3개 미만"으로 세고 함께 저장할 수 있다. 또 서로 다른 베이스캠프의 리더가 같은 신청자를 동시에 승인하면, 두 승인이 모두 상대의
// 멤버 행을 보지 못하고 기간이 겹치는 두 베이스캠프에 같은 회원을 넣을 수 있다. 그래서 서비스는 그 회원의 행을 쓰기 잠금으로 읽어 줄을 세운다.
// basecamp 모듈은 member 모듈의 클래스를 쓰지 않으므로, 다른 모듈 테이블을 SQL로만 읽는 기존 방식대로 잠금도 이 매퍼의 SQL로 건다.
@Mapper
public interface BasecampLockMapper {

    /** 호출하면 memberId인 회원 행을 쓰기 잠금으로 읽어 ID를 돌려준다. 회원이 없으면 null이다. */
    Long lockMember(@Param("memberId") long memberId);

    /**
     * 호출하면 basecampId인 베이스캠프에 속한 applicationId 신청 행을 쓰기 잠금으로 읽어 신청자의 회원 ID를 돌려준다. 그런 신청이 없으면 null이다.
     *
     * <p>신청자 ID를 잠그지 않는 조회로 얻으면 그 순간 읽기 스냅숏이 정해진다. 그러면 뒤이어 회원 행 잠금을 기다리는 동안 다른 트랜잭션이 커밋한
     * 멤버 행이 보이지 않는다. 그래서 신청자 ID도 잠금 읽기로 얻는다.
     */
    Long lockApplicantId(@Param("basecampId") long basecampId, @Param("applicationId") long applicationId);
}
