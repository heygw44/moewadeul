package com.moewadeul.common.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

// 수집 스레드가 DB를 읽으면 커넥션 풀이 찼을 때 수집도 풀을 기다린다. 스케줄러가 읽어 둔 값을 게이지가 돌려주게 한다.
public final class PolledSnapshot<T> {

    public static final long REFRESH_DELAY_MILLIS = 30_000;
    public static final Duration STALE_AFTER = Duration.ofMillis(3 * REFRESH_DELAY_MILLIS);

    private record Reading<T>(T value, Instant readAt) {}

    private final Supplier<T> reader;
    private final Clock clock;
    private volatile Reading<T> latest;

    public PolledSnapshot(Supplier<T> reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    public void refresh() {
        latest = new Reading<>(reader.get(), clock.instant());
    }

    // 아직 읽은 적이 없거나 마지막으로 읽은 지 STALE_AFTER가 넘으면 값이 아니라 NaN을 돌려준다. 낡은 값을 정상으로 보이게 하지 않는다.
    public double read(ToDoubleFunction<T> field) {
        Reading<T> reading = latest;
        if (reading == null
                || Duration.between(reading.readAt(), clock.instant()).compareTo(STALE_AFTER) > 0) {
            return Double.NaN;
        }
        return field.applyAsDouble(reading.value());
    }
}
