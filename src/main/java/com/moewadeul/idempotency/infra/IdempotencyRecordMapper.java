package com.moewadeul.idempotency.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyRecordMapper {

    int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
