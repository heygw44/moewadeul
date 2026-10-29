package com.moewadeul.common.audit;

import com.moewadeul.common.transaction.InsideTransaction;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AdminAuditRecorder {

    private final AdminAuditLogJpaRepository adminAuditLogRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public void record(
            long adminId, AdminAuditAction action, AdminAuditTargetType targetType, long targetId, Object detail) {
        InsideTransaction.require("감사 로그는 관리자 조치와 같은 트랜잭션에서 기록해야 한다.");
        String detailJson = detail == null ? null : toJson(detail);
        adminAuditLogRepository.save(
                AdminAuditLog.record(adminId, action, targetType, targetId, detailJson, Instant.now(clock)));
    }

    private String toJson(Object detail) {
        try {
            return jsonMapper.writeValueAsString(detail);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "감사 로그 detail을 JSON으로 바꿀 수 없습니다. detail 타입="
                            + detail.getClass().getName(),
                    e);
        }
    }
}
