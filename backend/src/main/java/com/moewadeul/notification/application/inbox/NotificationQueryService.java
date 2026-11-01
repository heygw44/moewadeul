package com.moewadeul.notification.application.inbox;

import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.notification.domain.Notification;
import com.moewadeul.notification.domain.NotificationRepository;
import com.moewadeul.notification.domain.NotificationSlice;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PageSlice<NotificationItem> list(long memberId, int page, int size) {
        NotificationSlice slice = notificationRepository.findPage(memberId, page, size);
        List<NotificationItem> content =
                slice.content().stream().map(NotificationQueryService::toItem).toList();
        return new PageSlice<>(content, page, size, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public long countUnread(long memberId) {
        return notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
    }

    private static NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
