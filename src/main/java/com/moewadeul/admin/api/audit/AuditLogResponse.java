package com.moewadeul.admin.api.audit;

import com.moewadeul.admin.application.audit.AuditLogView;
import com.moewadeul.common.audit.AdminAuditAction;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record AuditLogResponse(
        long auditLogId,
        long adminId,
        AdminAuditAction action,
        String targetType,
        long targetId,
        JsonNode detail,
        Instant createdAt) {

    static AuditLogResponse from(AuditLogView view) {
        return new AuditLogResponse(
                view.auditLogId(),
                view.adminId(),
                view.action(),
                view.targetType(),
                view.targetId(),
                view.detail(),
                view.createdAt());
    }
}
