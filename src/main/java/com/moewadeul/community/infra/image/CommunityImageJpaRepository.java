package com.moewadeul.community.infra.image;

import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityImageJpaRepository extends JpaRepository<CommunityImage, Long>, CommunityImageRepository {

    @Override
    CommunityImage saveAndFlush(CommunityImage image);

    @Override
    List<CommunityImage> findAllById(Iterable<Long> ids);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM CommunityImage i WHERE i.id IN :ids ORDER BY i.id")
    List<CommunityImage> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Override
    @Query("SELECT i.id FROM CommunityImage i WHERE i.postId = :postId ORDER BY i.displayOrder")
    List<Long> findIdsByPostId(@Param("postId") long postId);
}
