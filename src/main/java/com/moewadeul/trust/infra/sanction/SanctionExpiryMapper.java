package com.moewadeul.trust.infra.sanction;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SanctionExpiryMapper {

    int expireEnded(@Param("now") Instant now);
}
