package com.moewadeul.program.infra.enrollment;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProgramPaymentExpiryMapper {

    List<ExpiryTarget> selectDueTargets(
            @Param("now") Instant now, @Param("afterId") long afterId, @Param("limit") int limit);

    ExpiryTarget selectOverdueByMember(
            @Param("programId") long programId, @Param("memberId") long memberId, @Param("now") Instant now);

    int expireIfDue(@Param("id") long id, @Param("now") Instant now);
}
