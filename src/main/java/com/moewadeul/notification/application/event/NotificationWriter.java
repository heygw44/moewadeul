package com.moewadeul.notification.application.event;

import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.domain.Notification;
import com.moewadeul.notification.domain.NotificationEmailStatus;
import com.moewadeul.notification.domain.NotificationRepository;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final NotificationMailMapper mailMapper;
    private final Clock clock;

    @Transactional
    int write(long eventId, List<NotificationDraft> drafts) {
        Map<String, NotificationDraft> draftsByKey = new LinkedHashMap<>();
        for (NotificationDraft draft : drafts) {
            draftsByKey.putIfAbsent(dedupKey(eventId, draft.memberId()), draft);
        }
        if (draftsByKey.isEmpty()) {
            return 0;
        }
        Map<String, NotificationEmailStatus> emailStatuses = emailStatuses(draftsByKey);
        List<String> existingKeys = notificationRepository.findExistingDedupKeys(draftsByKey.keySet());
        existingKeys.forEach(draftsByKey::remove);
        Instant now = clock.instant();
        List<Notification> created = draftsByKey.entrySet().stream()
                .map(entry -> toNotification(entry.getKey(), entry.getValue(), emailStatuses.get(entry.getKey()), now))
                .toList();
        notificationRepository.saveAll(created);
        return created.size();
    }

    static String dedupKey(long eventId, long memberId) {
        return eventId + ":" + memberId;
    }

    private Map<String, NotificationEmailStatus> emailStatuses(Map<String, NotificationDraft> draftsByKey) {
        Map<String, Set<Long>> deliverableByLimitedType = new HashMap<>();
        Map<String, Integer> pendingByType = new HashMap<>();
        Map<String, NotificationEmailStatus> statuses = new HashMap<>();
        draftsByKey.forEach((key, draft) -> {
            Integer limit = NotificationEventTypes.EMAIL_LIMIT_PER_EVENT.get(draft.type());
            if (limit == null) {
                statuses.put(key, NotificationEmailStatus.PENDING);
                return;
            }
            Set<Long> deliverable = deliverableByLimitedType.computeIfAbsent(
                    draft.type(), type -> deliverableMemberIds(type, draftsByKey));
            int pending = pendingByType.getOrDefault(draft.type(), 0);
            if (deliverable.contains(draft.memberId()) && pending < limit) {
                statuses.put(key, NotificationEmailStatus.PENDING);
                pendingByType.put(draft.type(), pending + 1);
            } else {
                statuses.put(key, NotificationEmailStatus.SKIPPED);
            }
        });
        return statuses;
    }

    private Set<Long> deliverableMemberIds(String type, Map<String, NotificationDraft> draftsByKey) {
        List<Long> memberIds = draftsByKey.values().stream()
                .filter(draft -> draft.type().equals(type))
                .map(NotificationDraft::memberId)
                .toList();
        return new HashSet<>(
                mailMapper.selectDeliverableMemberIds(type, memberIds, NotificationEventTypes.DEFAULT_EMAIL_ENABLED));
    }

    private static Notification toNotification(
            String dedupKey, NotificationDraft draft, NotificationEmailStatus emailStatus, Instant now) {
        return Notification.create(
                draft.memberId(), draft.type(), draft.title(), draft.body(), draft.link(), dedupKey, emailStatus, now);
    }
}
