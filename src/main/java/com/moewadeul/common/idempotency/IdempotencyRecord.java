package com.moewadeul.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

// 기본 키를 호출하는 쪽이 정하므로 Spring Data의 save()는 새 엔티티인지 알 수 없다.
// Persistable로 새 것이라고 답해 곧바로 INSERT를 보내고, 기본 키 중복은 DB가 가려낸다.
@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecordId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord implements Persistable<IdempotencyRecordId> {

    @Id
    @Column(name = "member_id")
    private long memberId;

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "response_status")
    private Short responseStatus;

    // 문자열을 그대로 JSON 컬럼에 넣는다. Hibernate는 String 속성의 값을 다시 인용부호로 감싸지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    private IdempotencyRecord(long memberId, String idempotencyKey, String requestHash, Instant now) {
        this.memberId = memberId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = now;
    }

    public static IdempotencyRecord claim(long memberId, String idempotencyKey, String requestHash, Instant now) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등성 키가 비어 있습니다.");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("요청 해시가 비어 있습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("선점 시각이 null입니다.");
        }
        return new IdempotencyRecord(memberId, idempotencyKey, requestHash, now);
    }

    public IdempotencyRecord complete(int responseStatus, String responseBody) {
        if (responseBody == null) {
            throw new IllegalArgumentException("응답 본문이 null입니다.");
        }
        this.responseStatus = (short) responseStatus;
        this.responseBody = responseBody;
        return this;
    }

    public boolean isCompleted() {
        return responseStatus != null && responseBody != null;
    }

    public boolean matches(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    @Override
    public IdempotencyRecordId getId() {
        return new IdempotencyRecordId(memberId, idempotencyKey);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
