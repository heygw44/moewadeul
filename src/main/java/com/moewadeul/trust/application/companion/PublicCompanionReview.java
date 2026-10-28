package com.moewadeul.trust.application.companion;

import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

public record PublicCompanionReview(List<CompanionReviewTag> tags, String comment, Instant createdAt) {}
