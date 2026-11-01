package com.moewadeul.memory.application.report;

import com.moewadeul.common.moderation.ContentReportReason;

public record MemoryReportCommand(ContentReportReason reason, String content) {}
