package com.moewadeul.trust.application.companion;

import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class CompanionReviewTags {

    private CompanionReviewTags() {}

    static List<CompanionReviewTag> parse(String joined) {
        if (joined == null || joined.isEmpty()) {
            return List.of();
        }
        Set<CompanionReviewTag> tags = EnumSet.noneOf(CompanionReviewTag.class);
        Arrays.stream(joined.split(",")).map(CompanionReviewTag::valueOf).forEach(tags::add);
        return List.copyOf(tags);
    }
}
