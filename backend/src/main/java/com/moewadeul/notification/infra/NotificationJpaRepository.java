package com.moewadeul.notification.infra;

import com.moewadeul.notification.domain.Notification;
import com.moewadeul.notification.domain.NotificationRepository;
import com.moewadeul.notification.domain.NotificationSlice;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    @Override
    <S extends Notification> List<S> saveAll(Iterable<S> notifications);

    @Override
    Optional<Notification> findByIdAndMemberId(long id, long memberId);

    Slice<Notification> findByMemberIdOrderByIdDesc(long memberId, Pageable pageable);

    @Override
    default NotificationSlice findPage(long memberId, int page, int size) {
        Slice<Notification> slice = findByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size));
        return new NotificationSlice(slice.getContent(), slice.hasNext());
    }

    @Override
    long countByMemberIdAndReadAtIsNull(long memberId);

    @Override
    @Query("SELECT n.dedupKey FROM Notification n WHERE n.dedupKey IN :dedupKeys")
    List<String> findExistingDedupKeys(@Param("dedupKeys") Collection<String> dedupKeys);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.memberId = :memberId AND n.readAt IS NULL")
    int markAllRead(@Param("memberId") long memberId, @Param("now") Instant now);
}
