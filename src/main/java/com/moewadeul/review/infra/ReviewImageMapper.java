package com.moewadeul.review.infra;

import com.moewadeul.common.image.AttachableImageMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewImageMapper extends AttachableImageMapper {

    List<ReviewImageRow> selectByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);
}
