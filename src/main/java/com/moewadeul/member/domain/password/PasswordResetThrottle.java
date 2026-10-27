package com.moewadeul.member.domain.password;

import com.moewadeul.member.domain.password.PasswordResetRequestPolicy.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "password_reset_throttle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetThrottle {

    @Id
    @Column(name = "throttle_key")
    private String key;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "request_count")
    private int requestCount;

    @Column(name = "last_request_at")
    private Instant lastRequestAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private PasswordResetThrottle(String key, Instant now) {
        this.key = key;
        this.windowStart = now;
        this.requestCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PasswordResetThrottle open(String key, Instant now) {
        return new PasswordResetThrottle(key, now);
    }

    public Optional<Duration> blockedFor(Rule rule, Instant now) {
        return Stream.of(intervalRemaining(rule, now), limitRemaining(rule, now))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
    }

    public void record(Rule rule, Instant now) {
        if (blockedFor(rule, now).isPresent()) {
            throw new IllegalStateException("한도에 걸린 재설정 요청은 셀 수 없습니다.");
        }
        if (!now.isBefore(windowStart.plus(rule.window()))) {
            this.windowStart = now;
            this.requestCount = 0;
        }
        this.requestCount++;
        this.lastRequestAt = now;
        this.updatedAt = now;
    }

    private Optional<Duration> intervalRemaining(Rule rule, Instant now) {
        if (lastRequestAt == null || rule.minInterval().isZero()) {
            return Optional.empty();
        }
        Instant allowedAt = lastRequestAt.plus(rule.minInterval());
        if (!allowedAt.isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, allowedAt));
    }

    private Optional<Duration> limitRemaining(Rule rule, Instant now) {
        Instant windowEnd = windowStart.plus(rule.window());
        if (!now.isBefore(windowEnd) || requestCount < rule.maxRequests()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, windowEnd));
    }

    @Override
    public String toString() {
        return "PasswordResetThrottle[requestCount=" + requestCount + "]";
    }
}
