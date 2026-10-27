package com.moewadeul.memory.infra.like;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryLikeMapper {

    void insertIgnoringDuplicate(
            @Param("memoryId") long memoryId, @Param("memberId") long memberId, @Param("now") Instant now);

    void delete(@Param("memoryId") long memoryId, @Param("memberId") long memberId);

    long countByMemory(@Param("memoryId") long memoryId);
}
