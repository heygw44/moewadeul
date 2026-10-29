package com.moewadeul.community.infra.image;

import com.moewadeul.common.image.AttachableImageMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityImageMapper extends AttachableImageMapper {

    List<CommunityImageRow> selectByPostId(@Param("postId") long postId);
}
