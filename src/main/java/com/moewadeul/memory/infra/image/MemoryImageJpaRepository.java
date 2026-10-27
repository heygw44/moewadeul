package com.moewadeul.memory.infra.image;

import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryImageJpaRepository extends JpaRepository<MemoryImage, Long>, MemoryImageRepository {

    @Override
    MemoryImage saveAndFlush(MemoryImage image);

    @Override
    List<MemoryImage> findAllById(Iterable<Long> ids);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM MemoryImage i WHERE i.id IN :ids ORDER BY i.id")
    List<MemoryImage> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Override
    @Query("SELECT i.id FROM MemoryImage i WHERE i.memoryId = :memoryId ORDER BY i.displayOrder")
    List<Long> findIdsByMemoryId(@Param("memoryId") long memoryId);
}
