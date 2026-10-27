package com.moewadeul.admin.application.audit;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record AuditLogView(
        long auditLogId,
        long adminId,
        String action,
        String targetType,
        long targetId,
        JsonNode detail,
        Instant createdAt) {}
