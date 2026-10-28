package com.moewadeul.community.application.moderation;

public record AdminCommunityReasonCounts(
        long spam, long abuse, long illegalCamping, long privacy, long moneyScam, long other) {}
