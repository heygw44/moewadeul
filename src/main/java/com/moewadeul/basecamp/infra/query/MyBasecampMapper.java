package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MyBasecampMapper {

    List<MyBasecampRow> selectMine(
            @Param("memberId") long memberId,
            @Param("relation") BasecampRelation relation,
            @Param("status") BasecampStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
