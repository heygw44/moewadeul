package com.moewadeul.admin.application.audit;

import com.moewadeul.admin.infra.audit.AdminAuditLogMapper;
import com.moewadeul.admin.infra.audit.AdminAuditLogRow;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.paging.PageSlice;
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
    public PageSlice<AuditLogView> findLogs(
            Long adminId, String targetType, Instant from, Instant to, int page, int size) {
        AdminAuditTargetType parsedTargetType = EnumParser.parseNullable(
                AdminAuditTargetType.class,
                targetType,
                "대상 종류는 " + EnumParser.names(AdminAuditTargetType.class) + " 중 하나여야 해요.");
        List<AdminAuditLogRow> rows = adminAuditLogMapper.selectLogs(
                adminId, parsedTargetType, from, to, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        return PageSlice.of(rows, page, size).map(this::toView);
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
