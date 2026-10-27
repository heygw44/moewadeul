package com.moewadeul.member.domain.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "email_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    private static final int CODE_HASH_LENGTH = 64;
    private static final int REQUEST_IP_MAX_LENGTH = 45;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "outbox_event_id")
    private Long outboxEventId;

    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "request_ip")
    private String requestIp;

    // 컬럼이 TINYINT라서 Hibernate의 스키마 검증을 통과하려면 JDBC 타입을 맞춰야 한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private EmailVerification(long memberId, long outboxEventId, String codeHash, String requestIp, Instant now) {
        this.memberId = memberId;
        this.outboxEventId = outboxEventId;
        this.codeHash = codeHash;
        this.requestIp = requestIp;
        this.attemptCount = 0;
        this.expiresAt = now.plus(EmailVerificationPolicy.CODE_VALIDITY);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static EmailVerification issue(
            long memberId, long outboxEventId, String codeHash, String requestIp, Instant now) {
        requireCodeHash(codeHash);
        if (requestIp == null || requestIp.isBlank() || requestIp.length() > REQUEST_IP_MAX_LENGTH) {
            throw new IllegalArgumentException("요청 IP는 1~" + REQUEST_IP_MAX_LENGTH + "자여야 합니다.");
        }
        requireNow(now);
        return new EmailVerification(memberId, outboxEventId, codeHash, requestIp, now);
    }

    public void reissue(String codeHash, Instant now) {
        requireCodeHash(codeHash);
        requireNow(now);
        this.codeHash = codeHash;
        this.attemptCount = 0;
        this.expiresAt = now.plus(EmailVerificationPolicy.CODE_VALIDITY);
        this.updatedAt = now;
    }

    private static void requireCodeHash(String codeHash) {
        if (codeHash == null || codeHash.length() != CODE_HASH_LENGTH) {
            throw new IllegalArgumentException("인증 코드 해시는 " + CODE_HASH_LENGTH + "자여야 합니다.");
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("코드를 발급한 시각이 null입니다.");
        }
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasReachedAttemptLimit() {
        return attemptCount >= EmailVerificationPolicy.MAX_ATTEMPTS;
    }

    public boolean matches(String code) {
        return VerificationCode.matches(memberId, code, codeHash);
    }

    @Override
    public String toString() {
        return "EmailVerification[id=" + id + ", memberId=" + memberId + "]";
    }
}
