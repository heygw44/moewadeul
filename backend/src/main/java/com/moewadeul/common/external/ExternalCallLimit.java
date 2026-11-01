package com.moewadeul.common.external;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public final class ExternalCallLimit {

    private final String api;
    private final Semaphore permits;
    private final ExternalApiMetrics metrics;

    public ExternalCallLimit(String api, ExternalApiProperties properties, ExternalApiMetrics metrics) {
        this.api = api;
        this.permits = new Semaphore(properties.maxConcurrentCalls());
        this.metrics = metrics;
    }

    public <T> T call(Supplier<T> call, Supplier<? extends RuntimeException> rejection) {
        if (!permits.tryAcquire()) {
            metrics.recordRejected(api);
            throw rejection.get();
        }
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }
}
