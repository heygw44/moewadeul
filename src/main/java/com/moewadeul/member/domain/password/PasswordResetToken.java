package com.moewadeul.member.domain.password;

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

@Entity
@Table(name = "password_reset_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    private static final int TOKEN_HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private PasswordResetToken(long memberId, String tokenHash, Instant now) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = now.plus(PasswordResetPolicy.TOKEN_VALIDITY);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PasswordResetToken issue(long memberId, String tokenHash, Instant now) {
        if (tokenHash == null || tokenHash.length() != TOKEN_HASH_LENGTH) {
            throw new IllegalArgumentException("재설정 토큰 해시는 " + TOKEN_HASH_LENGTH + "자여야 합니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("토큰을 발급한 시각이 null입니다.");
        }
        return new PasswordResetToken(memberId, tokenHash, now);
    }

    // 메일 서버 장애가 길면 재시도가 유효 시간보다 늦게 나갈 수 있다. 그래서 다시 보낸 시각부터 다시 센다.
    // 이미 썼거나 새 요청으로 끊긴 토큰은 살리지 않는다.
    public boolean renewIfUnused(Instant now) {
        if (usedAt != null) {
            return false;
        }
        this.expiresAt = now.plus(PasswordResetPolicy.TOKEN_VALIDITY);
        this.updatedAt = now;
        return true;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "PasswordResetToken[id=" + id + ", memberId=" + memberId + "]";
    }
}
