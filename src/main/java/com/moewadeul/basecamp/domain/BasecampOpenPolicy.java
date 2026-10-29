package com.moewadeul.basecamp.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.profile.TrustLevel;
import com.moewadeul.common.schedule.CampingSchedule;
import java.time.LocalDate;
import java.util.Set;

public final class BasecampOpenPolicy {

    public static final TrustLevel MIN_TRUST_LEVEL = TrustLevel.IDENTITY_VERIFIED;

    public static final int MAX_OPEN_PER_LEADER = 3;

    public static final Set<BasecampStatus> OPEN_COUNTED_STATUSES =
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);

    private BasecampOpenPolicy() {}

    public static void requireSchedule(CampingSchedule schedule, LocalDate today) {
        if (!schedule.isBookableOn(today)) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID);
        }
    }

    public static void requireUnderOpenLimit(long openCount) {
        if (openCount >= MAX_OPEN_PER_LEADER) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_OPEN_LIMIT);
        }
    }
}
