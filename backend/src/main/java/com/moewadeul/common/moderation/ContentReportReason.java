package com.moewadeul.common.moderation;

// 추억 기록 신고는 커뮤니티 신고와 사유가 같다. 박지 신고 사유는 따로 둔다.
public enum ContentReportReason {
    SPAM,
    ABUSE,
    ILLEGAL_CAMPING,
    PRIVACY,
    MONEY_SCAM,
    OTHER
}
