package com.moewadeul.admin.infra.audit;

import java.time.Instant;

public record AdminAuditLogRow(
        long auditLogId,
        long adminId,
        String action,
        String targetType,
        long targetId,
        String detail,
        Instant createdAt) {}
