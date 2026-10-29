package com.moewadeul.basecamp.infra.lifecycle;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasecampAutoTransitionMapper {

    List<Long> selectDueForDepartureIds(
            @Param("dueStartDate") LocalDate dueStartDate, @Param("afterId") long afterId, @Param("limit") int limit);

    List<Long> selectDueForCompletionIds(
            @Param("today") LocalDate today, @Param("afterId") long afterId, @Param("limit") int limit);
}
