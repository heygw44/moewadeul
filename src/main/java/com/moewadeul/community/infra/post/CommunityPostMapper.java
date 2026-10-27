package com.moewadeul.community.infra.post;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityPostMapper {

    List<CommunityPostRow> selectActive(
            @Param("condition") CommunityPostListCondition condition,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countActive(@Param("condition") CommunityPostListCondition condition);

    Long selectActiveIdAfter(@Param("offset") int offset);

    Optional<CommunityPostRow> selectActiveById(@Param("postId") long postId, @Param("viewerId") Long viewerId);

    int addViewCount(@Param("postId") long postId, @Param("views") long views);
}
