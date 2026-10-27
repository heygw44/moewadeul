package com.moewadeul.basecamp.infra.lifecycle;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasecampSanctionCleanupMapper {

    List<Long> selectInProgressBasecampIds(@Param("memberId") long memberId);
}
