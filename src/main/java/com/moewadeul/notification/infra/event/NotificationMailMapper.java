package com.moewadeul.notification.infra.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMailMapper {

    List<NotificationMailRow> selectDueForUpdate(
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("defaultEmailEnabled") boolean defaultEmailEnabled);

    int lease(@Param("ids") Collection<Long> ids, @Param("leaseUntil") Instant leaseUntil);

    int releaseLease(@Param("ids") Collection<Long> ids, @Param("leaseUntil") Instant leaseUntil);

    int markSkipped(@Param("ids") Collection<Long> ids);

    int markSent(@Param("id") long id, @Param("now") Instant now);

    int markFailedAttempt(
            @Param("id") long id,
            @Param("transientFailure") boolean transientFailure,
            @Param("maxAttempts") int maxAttempts,
            @Param("retryDelayMillis") long retryDelayMillis,
            @Param("retryMaxDelayMillis") long retryMaxDelayMillis,
            @Param("now") Instant now);

    List<Long> selectDeliverableMemberIds(
            @Param("type") String type,
            @Param("memberIds") Collection<Long> memberIds,
            @Param("defaultEmailEnabled") boolean defaultEmailEnabled);

    int countSentAfter(@Param("since") Instant since);

    NotificationMailBacklogRow selectBacklog();
}
