package com.moewadeul.outbox.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxEventCleanupMapper {

    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int deleteFailedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
