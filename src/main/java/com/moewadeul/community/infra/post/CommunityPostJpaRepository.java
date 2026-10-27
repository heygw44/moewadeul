package com.moewadeul.community.infra.post;

import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepository {

    @Override
    CommunityPost saveAndFlush(CommunityPost post);

    @Override
    Optional<CommunityPost> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CommunityPost p WHERE p.id = :id")
    Optional<CommunityPost> findByIdForUpdate(@Param("id") long id);

    @Override
    void flush();
}
