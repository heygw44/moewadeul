package com.moewadeul.member.infra.password;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PasswordResetTokenMapper {

    int markUsed(@Param("id") long id, @Param("now") Instant now);

    int invalidateUnusedTokens(@Param("memberId") long memberId, @Param("now") Instant now);

    int deleteExpiredBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
