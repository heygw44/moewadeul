package com.moewadeul.common.audit;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AdminAuditRecorder {

    private final AdminAuditLogJpaRepository adminAuditLogRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public void record(long adminId, AdminAuditAction action, String targetType, long targetId, Object detail) {
        requireActiveTransaction();
        String detailJson = detail == null ? null : toJson(detail);
        adminAuditLogRepository.save(
                AdminAuditLog.record(adminId, action, targetType, targetId, detailJson, Instant.now(clock)));
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("감사 로그는 관리자 조치와 같은 트랜잭션 안에서 기록해야 합니다.");
        }
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
