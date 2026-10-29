package com.moewadeul.admin.infra.audit;

import com.moewadeul.common.audit.AdminAuditTargetType;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAuditLogMapper {

    List<AdminAuditLogRow> selectLogs(
            @Param("adminId") Long adminId,
            @Param("targetType") AdminAuditTargetType targetType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
