package com.moewadeul.memory.application.report;

/** 추억 기록 신고 요청. reason은 {@code MemoryReportReason}의 이름이고, content는 없어도 된다. */
public record MemoryReportCommand(String reason, String content) {}
