package com.moewadeul.member.infra.verification;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UnverifiedMemberMapper {

    List<Long> selectExpiredIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int deleteExpiredByIds(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);
}
