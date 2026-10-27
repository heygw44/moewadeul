package com.moewadeul.community.infra.comment;

import com.moewadeul.community.domain.comment.CommunityComment;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentJpaRepository
        extends JpaRepository<CommunityComment, Long>, CommunityCommentRepository {

    @Override
    CommunityComment saveAndFlush(CommunityComment comment);

    @Override
    Optional<CommunityComment> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CommunityComment c WHERE c.id = :id")
    Optional<CommunityComment> findByIdForUpdate(@Param("id") long id);

    @Override
    void flush();
}
