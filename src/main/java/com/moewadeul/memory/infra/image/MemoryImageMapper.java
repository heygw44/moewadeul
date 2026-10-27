package com.moewadeul.memory.infra.image;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryImageMapper {

    List<MemoryImageRow> selectByMemoryIds(@Param("memoryIds") Collection<Long> memoryIds);

    List<MemoryImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    List<MemoryImageRow> selectUnattachedByMember(
            @Param("memberId") long memberId, @Param("afterId") long afterId, @Param("limit") int limit);

    int deleteIfUnattached(@Param("imageId") long imageId);
}
