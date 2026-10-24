package com.moewadeul.memory.infra.image;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 상세는 기록의 사진을 순서대로 읽고, 정리 작업과 탈퇴 정리는 행을 한 건씩 조건부로 지운다.
// 둘 다 쿼리를 직접 쓰는 편이 분명해서 MyBatis를 쓴다.
@Mapper
public interface MemoryImageMapper {

    /** 호출하면 memoryIds인 기록들에 붙은 사진을 기록 ID, 기록 안 순서대로 읽는다. 호출하는 쪽은 빈 목록을 넘기지 않는다. */
    List<MemoryImageRow> selectByMemoryIds(@Param("memoryIds") Collection<Long> memoryIds);

    /** 호출하면 기록에 붙지 않았고 createdBefore보다 먼저 만든 사진을 ID 오름차순으로 최대 limit건 읽는다. */
    List<MemoryImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    /**
     * 호출하면 memberId인 회원이 올렸고 기록에 붙지 않은 사진 중 ID가 afterId보다 큰 것을 만든 시각과 상관없이 ID 오름차순으로 최대 limit건 읽는다.
     * 처음부터 읽으려면 afterId에 0을 넘긴다.
     */
    List<MemoryImageRow> selectUnattachedByMember(
            @Param("memberId") long memberId, @Param("afterId") long afterId, @Param("limit") int limit);

    /** 호출하면 imageId인 사진이 아직 기록에 붙지 않았을 때만 행을 지우고, 지운 행 수(0 또는 1)를 돌려준다. */
    int deleteIfUnattached(@Param("imageId") long imageId);
}
