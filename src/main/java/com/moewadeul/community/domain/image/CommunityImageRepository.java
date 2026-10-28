package com.moewadeul.community.domain.image;

import java.util.Collection;
import java.util.List;

public interface CommunityImageRepository {

    CommunityImage saveAndFlush(CommunityImage image);

    List<CommunityImage> findAllById(Iterable<Long> ids);

    List<CommunityImage> findAllByIdForUpdate(Collection<Long> ids);

    List<Long> findIdsByPostId(long postId);
}
