package com.pitchmap.trip.infra;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 내 일정 목록은 장소 이름과 유형을 함께 보여 줘야 한다. trip 모듈은 spot 모듈의 엔티티를 쓸 수 없어서,
// 이 매퍼가 spot 테이블을 조인하는 SQL을 직접 쓰고 결과는 조회 전용 record로만 받는다.
@Mapper
public interface SoloTripMapper {

    /** 호출하면 memberId인 회원의 일정 중 종료일이 today보다 뒤인 것을 출발일이 빠른 순서로 읽는다. 출발일이 같으면 ID가 작은 것이 먼저다. */
    List<SoloTripRow> selectUpcomingByMember(@Param("memberId") long memberId, @Param("today") LocalDate today);
}
