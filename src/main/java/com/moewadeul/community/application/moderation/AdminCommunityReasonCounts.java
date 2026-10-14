package com.moewadeul.community.application.moderation;

/** 검토 전 신고를 사유별로 센 값. */
public record AdminCommunityReasonCounts(
        long spam, long abuse, long illegalCamping, long privacy, long moneyScam, long other) {}
