package com.moewadeul.admin.application.audit;

import com.moewadeul.common.audit.AdminAuditAction;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record AuditLogView(
        long auditLogId,
        long adminId,
        AdminAuditAction action,
        String targetType,
        long targetId,
        JsonNode detail,
        Instant createdAt) {}
