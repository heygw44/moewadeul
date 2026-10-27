package com.moewadeul.trust.application.report;

import java.util.List;

public record AdminMemberReportPage(List<AdminMemberReportSummary> content, int page, int size, boolean hasNext) {}
