package com.moewadeul.member.domain.verification;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

public final class EmailVerificationPolicy {

    public static final Duration CODE_VALIDITY = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;

    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    public static final Duration DAILY_WINDOW = Duration.ofHours(24);

    public static final int DAILY_LIMIT = 5;

    public static final Duration IP_WINDOW = Duration.ofHours(1);
    public static final int IP_LIMIT = 20;

    public static final Duration RECORD_RETENTION = Duration.ofDays(7);

    private EmailVerificationPolicy() {}

    public record ResendHistory(
            Instant lastSentAt,
            int sentInDay,
            Instant oldestSentInDay,
            int sentByIpInHour,
            Instant oldestSentByIpInHour,
            boolean sendPending) {

        public ResendHistory {
            requireConsistent(sentInDay, oldestSentInDay);
            requireConsistent(sentByIpInHour, oldestSentByIpInHour);
        }

        private static void requireConsistent(int count, Instant oldest) {
            if (count < 0 || (count == 0) != (oldest == null)) {
                throw new IllegalArgumentException("발송 횟수와 가장 오래된 발송 시각이 서로 맞지 않습니다.");
            }
        }
    }

    public static Optional<Duration> resendBlockedFor(ResendHistory history, Instant now) {
        return Stream.of(
                        intervalRemaining(history, now),
                        limitRemaining(history.sentInDay(), DAILY_LIMIT, history.oldestSentInDay(), DAILY_WINDOW, now),
                        ipBlockedFor(history.sentByIpInHour(), history.oldestSentByIpInHour(), now),
                        pendingRemaining(history))
                .flatMap(Optional::stream)
                .filter(remaining -> remaining.isPositive())
                .max(Duration::compareTo);
    }

    public static Optional<Duration> ipBlockedFor(int sentByIpInHour, Instant oldestSentByIpInHour, Instant now) {
        return limitRemaining(sentByIpInHour, IP_LIMIT, oldestSentByIpInHour, IP_WINDOW, now)
                .filter(remaining -> remaining.isPositive());
    }

    private static Optional<Duration> intervalRemaining(ResendHistory history, Instant now) {
        return Optional.ofNullable(history.lastSentAt())
                .map(lastSentAt -> Duration.between(now, lastSentAt.plus(RESEND_INTERVAL)));
    }

    private static Optional<Duration> limitRemaining(
            int sent, int limit, Instant oldestSent, Duration window, Instant now) {
        if (sent < limit) {
            return Optional.empty();
        }
        return Optional.ofNullable(oldestSent).map(oldest -> Duration.between(now, oldest.plus(window)));
    }

    private static Optional<Duration> pendingRemaining(ResendHistory history) {
        if (!history.sendPending()) {
            return Optional.empty();
        }
        return Optional.of(RESEND_INTERVAL);
    }
}
