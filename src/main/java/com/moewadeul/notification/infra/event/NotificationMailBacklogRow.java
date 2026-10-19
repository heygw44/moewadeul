package com.moewadeul.notification.infra.event;

import java.time.Instant;

/**
 * 아직 보내지 못한 알림 메일의 현황. PENDING 알림이 없으면 {@code oldestPendingCreatedAt}은 null이다.
 * MyBatis가 이름으로 매핑하므로, 구성요소 이름은 SELECT의 별칭과 같아야 한다.
 */
public record NotificationMailBacklogRow(long pendingCount, long failedCount, Instant oldestPendingCreatedAt) {}
