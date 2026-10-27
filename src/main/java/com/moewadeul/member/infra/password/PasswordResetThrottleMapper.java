package com.moewadeul.member.infra.password;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PasswordResetThrottleMapper {

    int insertIfAbsent(@Param("key") String key, @Param("now") Instant now);

    int deleteStale(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
