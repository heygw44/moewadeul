package com.moewadeul.trip.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 같은 회원이 일정 등록을 동시에 보내면, 두 요청이 서로의 새 일정을 보지 못하고 개수 제한과 밤 겹침 검사를 함께 통과할 수 있다.
// 그래서 등록 트랜잭션은 맨 처음에 회원 행을 쓰기 잠금으로 읽어 같은 회원의 등록을 한 줄로 세운다.
// 회원 행은 늘 있으므로 빈 범위에 걸리는 잠금끼리 교착 상태가 날 일이 없다. trip 모듈은 member 모듈의 클래스를 쓰지 않으므로,
// 다른 모듈 테이블을 SQL로만 읽는 기존 방식대로 잠금도 이 매퍼의 SQL로 건다.
@Mapper
public interface SoloTripMemberLockMapper {

    /** 호출하면 memberId인 회원 행을 쓰기 잠금으로 읽어 ID를 돌려준다. 회원이 없으면 null이다. */
    Long lockMember(@Param("memberId") long memberId);
}
