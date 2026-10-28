package com.moewadeul.basecamp.infra.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasecampApplicationMapper {

    List<BasecampApplicationRow> selectByStatus(
            @Param("basecampId") long basecampId,
            @Param("status") BasecampApplicationStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
