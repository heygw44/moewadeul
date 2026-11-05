package com.moewadeul.community.infra.image;

import com.moewadeul.common.image.infra.AttachableImageMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityImageMapper extends AttachableImageMapper {

    List<CommunityImageRow> selectByPostId(@Param("postId") long postId);

    default List<CommunityImageRow> selectByPostIds(Collection<Long> postIds) {
        return postIds.isEmpty() ? List.of() : selectByNonEmptyPostIds(postIds);
    }

    List<CommunityImageRow> selectByNonEmptyPostIds(@Param("postIds") Collection<Long> postIds);
}
