package com.moewadeul.admin.infra.audit;

import com.moewadeul.common.audit.AdminAuditAction;
import java.time.Instant;

public record AdminAuditLogRow(
        long auditLogId,
        long adminId,
        AdminAuditAction action,
        String targetType,
        long targetId,
        String detail,
        Instant createdAt) {}
