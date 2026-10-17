package com.moewadeul.notification.infra.setting;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 설정 변경은 그 회원의 설정 행을 모두 지우고 다시 넣는다. 설정 행이 아직 없는 회원의 변경 두 건이 동시에 들어오면,
// 두 DELETE가 같은 빈 범위에 간격 잠금을 잡고 서로의 INSERT를 기다리다 교착 상태가 난다.
// 그래서 변경 트랜잭션은 맨 처음에 회원 행을 쓰기 잠금으로 읽어 같은 회원의 변경을 한 줄로 세운다. 회원 행은 늘 있으므로
// 이 잠금 자체는 빈 범위에 걸리지 않는다. notification 모듈은 member 모듈의 클래스를 쓰지 않으므로 잠금도 이 매퍼의 SQL로 건다.
@Mapper
public interface NotificationSettingMemberLockMapper {

    /** 호출하면 memberId인 회원 행을 쓰기 잠금으로 읽어 ID를 돌려준다. 회원이 없으면 null이다. */
    Long lockMember(@Param("memberId") long memberId);
}
