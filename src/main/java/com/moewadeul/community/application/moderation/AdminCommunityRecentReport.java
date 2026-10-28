package com.moewadeul.community.application.moderation;

import java.time.Instant;

public record AdminCommunityRecentReport(String reason, String content, Instant createdAt) {}
