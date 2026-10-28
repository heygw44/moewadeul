package com.moewadeul.memory.infra;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryMapper {

    Optional<MemoryRow> selectById(@Param("memoryId") long memoryId, @Param("viewerId") Long viewerId);

    List<MemorySummaryRow> selectByMember(@Param("memberId") long memberId, @Param("visibility") String visibility);

    List<MemoryFeedRow> selectPublicFeed(
            @Param("viewerId") Long viewerId, @Param("limit") int limit, @Param("offset") long offset);

    boolean existsActiveMember(@Param("memberId") long memberId);

    int deleteByMember(@Param("memberId") long memberId);
}
