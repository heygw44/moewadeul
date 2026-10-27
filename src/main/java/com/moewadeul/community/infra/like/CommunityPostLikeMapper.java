package com.moewadeul.community.infra.like;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityPostLikeMapper {

    void insertIgnoringDuplicate(
            @Param("postId") long postId, @Param("memberId") long memberId, @Param("now") Instant now);

    void delete(@Param("postId") long postId, @Param("memberId") long memberId);

    String lockPostStatus(@Param("postId") long postId);

    void refreshLikeCount(@Param("postId") long postId);

    long selectLikeCount(@Param("postId") long postId);
}
