package com.moewadeul.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 서버 한 대 메모리에만 있으므로 재시작하면 비고, 인스턴스를 늘리면 인스턴스마다 따로 센다. */
public final class IpTokenBucket {

    private static final long MAX_IPS = 100_000;

    private final Duration window;
    private final Duration refillInterval;
    private final Cache<String, Instant> fullAgainAt;
    private final Clock clock;

    public IpTokenBucket(int capacity, Duration window, Clock clock) {
        this.window = window;
        this.refillInterval = window.dividedBy(capacity);
        this.clock = clock;
        // 마지막 기록 뒤 한 창이 지나면 버킷은 가득 찬 상태라 지워도 같다.
        this.fullAgainAt = Caffeine.newBuilder()
                .maximumSize(MAX_IPS)
                .expireAfterWrite(window)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /** 토큰을 하나 쓴다. 토큰이 없으면 다음 토큰까지 남은 시간을 돌려준다. */
    public Optional<Duration> tryTake(String ip) {
        Instant now = clock.instant();
        Duration[] rejectedFor = new Duration[1];
        fullAgainAt.asMap().compute(ip, (key, fullAgain) -> {
            Instant base = fullAgain == null || fullAgain.isBefore(now) ? now : fullAgain;
            Duration untilToken = Duration.between(now, base).minus(window.minus(refillInterval));
            if (untilToken.isPositive()) {
                rejectedFor[0] = untilToken;
                return base;
            }
            return base.plus(refillInterval);
        });
        return Optional.ofNullable(rejectedFor[0]);
    }

    public void giveBack(String ip) {
        Instant now = clock.instant();
        fullAgainAt.asMap().computeIfPresent(ip, (key, fullAgain) -> {
            Instant restored = fullAgain.minus(refillInterval);
            return restored.isAfter(now) ? restored : null;
        });
    }

    public void clear() {
        fullAgainAt.invalidateAll();
    }
}
