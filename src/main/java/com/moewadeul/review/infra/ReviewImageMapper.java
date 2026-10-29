package com.moewadeul.review.infra;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewImageMapper {

    List<ReviewImageRow> selectByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    List<ReviewImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    int deleteIfUnattached(@Param("imageId") long imageId);

    Long lockMember(@Param("memberId") long memberId);

    List<Instant> selectUnattachedCreatedAtsAfter(
            @Param("memberId") long memberId, @Param("createdAfter") Instant createdAfter, @Param("limit") int limit);
}
