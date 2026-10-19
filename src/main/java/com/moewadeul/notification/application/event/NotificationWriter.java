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

/**
 * 이벤트 하나에서 만든 알림을 한 트랜잭션으로 저장한다.
 *
 * <p>아웃박스는 같은 이벤트를 적어도 한 번 전달한다. 그래서 알림마다 "{이벤트 ID}:{회원 ID}" 키를 만들고, 이미 저장된 키는 건너뛴다.
 * 조회와 저장 사이에 다른 발행기가 같은 키를 먼저 저장하면 DB의 유니크 제약이 위반 예외를 던진다.
 * 이 예외는 잡지 않고 그대로 올린다. 발행기가 이벤트를 다시 처리하면 이번에는 모든 키가 이미 있어서 아무것도 저장하지 않고 끝난다.
 *
 * <p>메일은 여기서 보내지 않는다. 알림마다 메일 상태를 PENDING으로 두면 메일 발송 작업이 따로 보낸다. 이벤트 하나의 메일 상한이 있는 종류는
 * 메일을 받을 수 있는 회원을 drafts 순서대로 상한까지만 PENDING으로 두고 나머지는 SKIPPED로 둔다.
 */
@Service
@RequiredArgsConstructor
class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final NotificationMailMapper mailMapper;
    private final Clock clock;

    /** 호출하면 drafts 가운데 아직 저장되지 않은 알림만 저장하고, 새로 저장한 건수를 돌려준다. */
    @Transactional
    int write(long eventId, List<NotificationDraft> drafts) {
        Map<String, NotificationDraft> draftsByKey = new LinkedHashMap<>();
        for (NotificationDraft draft : drafts) {
            draftsByKey.putIfAbsent(dedupKey(eventId, draft.memberId()), draft);
        }
        if (draftsByKey.isEmpty()) {
            return 0;
        }
        // 이미 저장된 알림을 빼기 전에 상태를 정한다. 같은 이벤트를 다시 처리해도 상한 안에 드는 회원이 바뀌지 않게 하려는 순서다.
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
