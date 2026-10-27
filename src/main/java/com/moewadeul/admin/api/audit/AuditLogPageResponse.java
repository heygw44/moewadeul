package com.moewadeul.admin.api.audit;

import com.moewadeul.admin.application.audit.AuditLogPage;
import java.util.List;

public record AuditLogPageResponse(List<AuditLogResponse> content, int page, int size, boolean hasNext) {

    static AuditLogPageResponse from(AuditLogPage page) {
        return new AuditLogPageResponse(
                page.content().stream().map(AuditLogResponse::from).toList(), page.page(), page.size(), page.hasNext());
    }
}
