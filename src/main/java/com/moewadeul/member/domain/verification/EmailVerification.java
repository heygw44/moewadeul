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

/**
 * 인증 코드를 발송한 기록 한 건. 발송 요청 이벤트 하나에 행이 하나이고, 코드 원값은 저장하지 않고 해시만 저장한다.
 * 같은 이벤트를 다시 처리하면 새 행을 만들지 않고 {@link #reissue}로 같은 행의 코드를 바꾼다.
 * 시도 횟수와 인증 시각은 동시 요청에서도 한 번만 바뀌어야 해서 MyBatis 조건부 UPDATE로 바꾼다.
 */
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

    /** @param codeHash {@link VerificationCode#hash(long, String)}의 결과. 코드 원값을 넘기면 안 된다. */
    public static EmailVerification issue(
            long memberId, long outboxEventId, String codeHash, String requestIp, Instant now) {
        requireCodeHash(codeHash);
        if (requestIp == null || requestIp.isBlank() || requestIp.length() > REQUEST_IP_MAX_LENGTH) {
            throw new IllegalArgumentException("요청 IP는 1~" + REQUEST_IP_MAX_LENGTH + "자여야 합니다.");
        }
        requireNow(now);
        return new EmailVerification(memberId, outboxEventId, codeHash, requestIp, now);
    }

    /**
     * 호출하면 같은 발송 요청을 다시 처리하면서 만든 새 코드로 바꾼다. 이전 코드는 무효가 되고 시도 횟수는 0으로 돌아가며 만료는
     * {@code now}부터 다시 센다. 발송 요청을 처음 처리한 시각({@code createdAt})은 그대로 둬서 재발송 한도에는 한 번만 센다.
     *
     * @param codeHash {@link VerificationCode#hash(long, String)}의 결과. 코드 원값을 넘기면 안 된다.
     */
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

    /** 호출하면 유효 시간이 끝났는지 알려 준다. 만료 시각 정각부터 만료다. */
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
