package com.moewadeul.admin.application.audit;

import com.moewadeul.admin.infra.audit.AdminAuditLogMapper;
import com.moewadeul.admin.infra.audit.AdminAuditLogRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AdminAuditLogMapper adminAuditLogMapper;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public AuditLogPage findLogs(Long adminId, String targetType, Instant from, Instant to, int page, int size) {
        long offset = (long) page * size;
        List<AdminAuditLogRow> rows = adminAuditLogMapper.selectLogs(adminId, targetType, from, to, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<AuditLogView> content = rows.stream().limit(size).map(this::toView).toList();
        return new AuditLogPage(content, page, size, hasNext);
    }

    private AuditLogView toView(AdminAuditLogRow row) {
        JsonNode detail = row.detail() == null ? null : jsonMapper.readTree(row.detail());
        return new AuditLogView(
                row.auditLogId(),
                row.adminId(),
                row.action(),
                row.targetType(),
                row.targetId(),
                detail,
                row.createdAt());
    }
}
