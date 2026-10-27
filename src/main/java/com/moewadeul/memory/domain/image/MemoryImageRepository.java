package com.moewadeul.memory.domain.image;

import java.util.Collection;
import java.util.List;

public interface MemoryImageRepository {

    MemoryImage saveAndFlush(MemoryImage image);

    List<MemoryImage> findAllById(Iterable<Long> ids);

    List<MemoryImage> findAllByIdForUpdate(Collection<Long> ids);

    List<Long> findIdsByMemoryId(long memoryId);
}
