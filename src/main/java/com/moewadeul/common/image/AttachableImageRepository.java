package com.moewadeul.common.image;

import java.util.Collection;
import java.util.List;

public interface AttachableImageRepository<T extends AttachableImage> {

    T saveAndFlush(T image);

    List<T> findAllById(Iterable<Long> ids);

    List<T> findAllByIdForUpdate(Collection<Long> ids);

    List<Long> findIdsByParentId(long parentId);
}
