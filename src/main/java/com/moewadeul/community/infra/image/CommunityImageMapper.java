package com.moewadeul.community.infra.image;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityImageMapper {

    List<CommunityImageRow> selectByPostId(@Param("postId") long postId);

    List<CommunityImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    int deleteIfUnattached(@Param("imageId") long imageId);

    Long lockMember(@Param("memberId") long memberId);

    List<Instant> selectUnattachedCreatedAtsAfter(
            @Param("memberId") long memberId, @Param("createdAfter") Instant createdAfter, @Param("limit") int limit);
}
