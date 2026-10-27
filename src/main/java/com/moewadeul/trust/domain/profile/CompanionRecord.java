package com.moewadeul.trust.domain.profile;

import java.time.Duration;

public record CompanionRecord(
        int completedCompanions, int receivedReviews, int rejoinWanted, int recentEarlyLeaves, boolean recentSanction) {

    public static final int REQUIRED_COMPLETED_COMPANIONS = 3;

    public static final int REQUIRED_REJOIN_PERCENT = 80;

    public static final int EARLY_LEAVE_LIMIT = 3;

    public static final Duration RECENT_PERIOD = Duration.ofDays(180);

    public static final CompanionRecord NONE = new CompanionRecord(0, 0, 0, 0, false);

    public Integer rejoinRate() {
        if (receivedReviews == 0) {
            return null;
        }
        return (int) ((long) rejoinWanted * 100 / receivedReviews);
    }

    public boolean meetsTrustedCondition() {
        return completedCompanions >= REQUIRED_COMPLETED_COMPANIONS
                && receivedReviews > 0
                && (long) rejoinWanted * 100 >= (long) REQUIRED_REJOIN_PERCENT * receivedReviews
                && recentEarlyLeaves < EARLY_LEAVE_LIMIT
                && !recentSanction;
    }
}
