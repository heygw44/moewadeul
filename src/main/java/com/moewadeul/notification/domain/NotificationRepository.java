package com.moewadeul.notification.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    <S extends Notification> List<S> saveAll(Iterable<S> notifications);

    Optional<Notification> findByIdAndMemberId(long id, long memberId);

    NotificationSlice findPage(long memberId, int page, int size);

    long countByMemberIdAndReadAtIsNull(long memberId);

    List<String> findExistingDedupKeys(Collection<String> dedupKeys);

    int markAllRead(long memberId, Instant now);
}
