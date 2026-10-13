package com.moewadeul.community.application.report;

/** 글이나 댓글 신고 요청. reason은 {@code CommunityReportReason}의 이름이고, content는 없어도 된다. */
public record CommunityReportCommand(String reason, String content) {}
