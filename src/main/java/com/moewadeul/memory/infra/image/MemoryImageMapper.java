package com.moewadeul.memory.infra.image;

import com.moewadeul.common.image.infra.AttachableImageMapper;
import com.moewadeul.common.image.infra.UnattachedImageRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryImageMapper extends AttachableImageMapper {

    default List<MemoryImageRow> selectByMemoryIds(Collection<Long> memoryIds) {
        return memoryIds.isEmpty() ? List.of() : selectByNonEmptyMemoryIds(memoryIds);
    }

    List<MemoryImageRow> selectByNonEmptyMemoryIds(@Param("memoryIds") Collection<Long> memoryIds);

    List<UnattachedImageRow> selectUnattachedByMember(
            @Param("memberId") long memberId, @Param("afterId") long afterId, @Param("limit") int limit);
}
