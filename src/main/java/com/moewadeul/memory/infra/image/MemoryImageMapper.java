package com.moewadeul.memory.infra.image;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryImageMapper {

    default List<MemoryImageRow> selectByMemoryIds(Collection<Long> memoryIds) {
        return memoryIds.isEmpty() ? List.of() : selectByNonEmptyMemoryIds(memoryIds);
    }

    List<MemoryImageRow> selectByNonEmptyMemoryIds(@Param("memoryIds") Collection<Long> memoryIds);

    List<MemoryImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    List<MemoryImageRow> selectUnattachedByMember(
            @Param("memberId") long memberId, @Param("afterId") long afterId, @Param("limit") int limit);

    int deleteIfUnattached(@Param("imageId") long imageId);
}
