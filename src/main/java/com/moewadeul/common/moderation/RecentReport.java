package com.moewadeul.common.moderation;

import java.time.Instant;

public record RecentReport(String reason, String content, Instant createdAt) {

    public static final int MAX_PER_TARGET = 5;
}
