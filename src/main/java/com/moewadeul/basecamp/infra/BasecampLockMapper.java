package com.moewadeul.basecamp.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasecampLockMapper {

    Long lockMember(@Param("memberId") long memberId);

    /**
     * 신청자 ID를 잠그지 않는 조회로 얻으면 그 순간 읽기 스냅숏이 정해진다. 그러면 뒤이어 회원 행 잠금을 기다리는 동안 다른 트랜잭션이 커밋한
     * 멤버 행이 보이지 않는다. 그래서 신청자 ID도 잠금 읽기로 얻는다.
     */
    Long lockApplicantId(@Param("basecampId") long basecampId, @Param("applicationId") long applicationId);
}
