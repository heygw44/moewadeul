package com.moewadeul.trust.domain.companion;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public enum CompanionReviewTag {
    ON_TIME,
    LEAVE_NO_TRACE,
    CONSIDERATE,
    WELL_PREPARED,
    LATE,
    NO_SHOW,
    LITTERING;

    public static final int TOP_TAG_LIMIT = 3;

    public static List<CompanionReviewTag> top(Map<CompanionReviewTag, Long> counts) {
        return Arrays.stream(values())
                .filter(tag -> counts.getOrDefault(tag, 0L) > 0)
                .sorted(Comparator.comparing((CompanionReviewTag tag) -> counts.get(tag))
                        .reversed()
                        .thenComparing(Enum::ordinal))
                .limit(TOP_TAG_LIMIT)
                .toList();
    }
}
