package com.moewadeul.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private long adminId;

    @Enumerated(EnumType.STRING)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private AdminAuditTargetType targetType;

    @Column(name = "target_id")
    private long targetId;

    // 문자열을 그대로 JSON 컬럼에 넣는다. Hibernate는 String 속성의 값을 다시 인용부호로 감싸지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;

    @Column(name = "created_at")
    private Instant createdAt;

    private AdminAuditLog(
            long adminId,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            long targetId,
            String detail,
            Instant now) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = now;
    }

    public static AdminAuditLog record(
            long adminId,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            long targetId,
            String detailJson,
            Instant now) {
        if (action == null || targetType == null || now == null) {
            throw new IllegalArgumentException("감사 로그의 조치 종류, 대상 종류, 기록 시각이 null입니다.");
        }
        return new AdminAuditLog(adminId, action, targetType, targetId, detailJson, now);
    }
}
