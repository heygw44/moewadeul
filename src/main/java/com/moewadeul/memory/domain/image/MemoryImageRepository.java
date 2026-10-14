package com.moewadeul.memory.domain.image;

import java.util.Collection;
import java.util.List;

public interface MemoryImageRepository {

    MemoryImage saveAndFlush(MemoryImage image);

    /** 호출하면 ids인 사진을 잠그지 않고 읽는다. 없는 ID는 결과에 없다. */
    List<MemoryImage> findAllById(Iterable<Long> ids);

    /** 호출하면 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 트랜잭션 안에서만 부른다. 없는 ID는 결과에 없다. */
    List<MemoryImage> findAllByIdForUpdate(Collection<Long> ids);

    /** 호출하면 memoryId인 기록에 붙은 사진의 ID만 기록 안 순서대로 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않는다. */
    List<Long> findIdsByMemoryId(long memoryId);
}
