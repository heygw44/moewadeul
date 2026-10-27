package com.moewadeul.outbox.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxEventMapper {

    List<Long> selectClaimableIds(@Param("now") Instant now, @Param("limit") int limit);

    int lease(@Param("ids") List<Long> ids, @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

    int releaseLease(
            @Param("ids") List<Long> ids, @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

    List<OutboxMessageRow> selectByIds(@Param("ids") List<Long> ids);

    int markPublished(@Param("id") long id, @Param("now") Instant now);

    int markFailedAttempt(
            @Param("id") long id,
            @Param("error") String error,
            @Param("transientFailure") boolean transientFailure,
            @Param("maxAttempts") int maxAttempts,
            @Param("retryDelayMillis") long retryDelayMillis,
            @Param("retryMaxDelayMillis") long retryMaxDelayMillis,
            @Param("now") Instant now);

    OutboxBacklogRow selectBacklog();
}
