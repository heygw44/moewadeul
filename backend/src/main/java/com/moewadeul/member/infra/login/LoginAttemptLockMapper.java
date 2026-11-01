package com.moewadeul.member.infra.login;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginAttemptLockMapper {

    int lock(@Param("key") String key, @Param("now") Instant now);

    int deleteIdleBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
