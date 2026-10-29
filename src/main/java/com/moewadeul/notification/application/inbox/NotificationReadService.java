package com.moewadeul.notification.application.inbox;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.notification.domain.Notification;
import com.moewadeul.notification.domain.NotificationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationReadService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional
    public void markRead(long memberId, long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(BusinessException::notFound);
        notification.markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(long memberId) {
        notificationRepository.markAllRead(memberId, clock.instant());
    }
}
