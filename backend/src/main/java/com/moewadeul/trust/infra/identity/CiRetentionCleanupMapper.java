package com.moewadeul.trust.infra.identity;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CiRetentionCleanupMapper {

    int deleteRetentionExpired(@Param("now") Instant now, @Param("limit") int limit);
}
