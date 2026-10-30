package com.moewadeul.community.domain.post;

import java.util.Optional;

public interface CommunityPostRepository {

    CommunityPost saveAndFlush(CommunityPost post);

    Optional<CommunityPost> findById(Long id);

    Optional<CommunityPost> findByIdForUpdate(long id);

    Optional<CommunityPost> findByIdForShare(long id);

    void flush();
}
