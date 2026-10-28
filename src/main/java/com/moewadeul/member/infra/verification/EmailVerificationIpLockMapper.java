package com.moewadeul.member.infra.verification;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmailVerificationIpLockMapper {

    int lock(@Param("requestIp") String requestIp, @Param("now") Instant now);

    int deleteIdleBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
