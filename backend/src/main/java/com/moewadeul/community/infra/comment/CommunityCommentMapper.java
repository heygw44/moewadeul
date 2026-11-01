package com.moewadeul.community.infra.comment;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityCommentMapper {

    List<CommunityCommentRow> selectTopLevel(
            @Param("postId") long postId, @Param("offset") long offset, @Param("limit") int limit);

    List<CommunityCommentRow> selectActiveReplies(@Param("parentIds") List<Long> parentIds);

    Optional<CommunityCommentRow> selectById(@Param("commentId") long commentId);
}
