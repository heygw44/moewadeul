package com.moewadeul.spot.infra.bakji;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BakjiDuplicateMapper {

    List<BakjiDuplicateRow> selectDuplicates(@Param("dup") BakjiDuplicateCondition condition);
}
