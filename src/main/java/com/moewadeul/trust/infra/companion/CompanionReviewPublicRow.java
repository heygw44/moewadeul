package com.moewadeul.trust.infra.companion;

import java.time.Instant;

public record CompanionReviewPublicRow(String tags, String comment, Instant createdAt) {}
