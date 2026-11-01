package com.moewadeul.member.infra.login;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginHistoryCleanupMapper {

    int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
