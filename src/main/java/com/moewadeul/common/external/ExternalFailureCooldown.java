package com.moewadeul.common.external;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ExternalFailureCooldown {

    private final String api;
    private final long cooldownMillis;
    private final Clock clock;
    private final ExternalApiMetrics metrics;
    private final AtomicLong restUntil = new AtomicLong();

    public ExternalFailureCooldown(
            String api, ExternalApiProperties properties, Clock clock, ExternalApiMetrics metrics) {
        this.api = api;
        this.cooldownMillis = properties.failureCooldown().toMillis();
        this.clock = clock;
        this.metrics = metrics;
    }

    public <T> T call(
            Supplier<T> call, Predicate<RuntimeException> isOutage, Supplier<? extends RuntimeException> skipped) {
        if (cooldownMillis == 0) {
            return call.get();
        }
        if (!tryStart()) {
            metrics.recordSkipped(api);
            throw skipped.get();
        }
        try {
            T result = call.get();
            restUntil.set(0);
            return result;
        } catch (RuntimeException e) {
            restUntil.set(isOutage.test(e) ? clock.millis() + cooldownMillis : 0);
            throw e;
        }
    }

    private boolean tryStart() {
        long until = restUntil.get();
        if (until == 0) {
            return true;
        }
        long now = clock.millis();
        if (now < until) {
            return false;
        }
        return restUntil.compareAndSet(until, now + cooldownMillis);
    }
}
