package com.moewadeul.admin.application.audit;

import java.util.List;

public record AuditLogPage(List<AuditLogView> content, int page, int size, boolean hasNext) {}
