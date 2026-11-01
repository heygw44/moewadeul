package com.moewadeul.community.domain.comment;

import java.util.Optional;

public interface CommunityCommentRepository {

    CommunityComment saveAndFlush(CommunityComment comment);

    Optional<CommunityComment> findById(Long id);

    Optional<CommunityComment> findByIdForUpdate(long id);

    void flush();
}
