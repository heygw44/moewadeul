package com.moewadeul.basecamp.infra.lifecycle;

import java.time.Instant;
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

    String selectStatusForUpdate(@Param("id") long id);

    int countActiveMembers(@Param("id") long id);

    List<Long> selectActiveMemberIds(@Param("id") long id);

    int confirmIfBeforeConfirm(@Param("id") long id, @Param("now") Instant now);

    int cancelForNotEnoughMembers(@Param("id") long id, @Param("now") Instant now);

    int completeIfConfirmed(@Param("id") long id, @Param("today") LocalDate today, @Param("now") Instant now);

    int expirePendingApplications(@Param("basecampId") long basecampId, @Param("now") Instant now);
}
