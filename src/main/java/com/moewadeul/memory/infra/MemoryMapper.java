package com.moewadeul.memory.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 상세와 목록은 작성자 닉네임과 장소 이름을 member·spot 테이블에서 함께 읽어야 하고, 목록은 기록마다 첫 사진과 사진 수를 센다.
// 탈퇴 정리는 회원의 기록을 한 문장으로 지운다. 셋 다 쿼리를 직접 쓰는 편이 분명해서 MyBatis를 쓴다.
@Mapper
public interface MemoryMapper {

    /** 호출하면 memoryId인 기록을 읽는다. 공개 범위는 거르지 않으므로 호출하는 쪽이 볼 수 있는 사람인지 판단한다. */
    Optional<MemoryRow> selectById(@Param("memoryId") long memoryId);

    /** 호출하면 memberId인 회원의 기록을 모두 방문일이 늦은 순서로, 방문일이 같으면 ID가 큰 순서로 읽는다. */
    List<MemorySummaryRow> selectByMember(@Param("memberId") long memberId);

    /** 호출하면 memberId인 회원의 기록을 모두 지우고 지운 행 수를 돌려준다. 붙어 있던 사진은 FK가 붙지 않은 상태로 바꾼다. */
    int deleteByMember(@Param("memberId") long memberId);
}
