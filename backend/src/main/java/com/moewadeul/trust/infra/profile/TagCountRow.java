package com.moewadeul.trust.infra.profile;

import com.moewadeul.trust.domain.companion.CompanionReviewTag;

public record TagCountRow(CompanionReviewTag tag, long count) {}
