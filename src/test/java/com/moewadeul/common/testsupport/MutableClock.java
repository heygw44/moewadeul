package com.moewadeul.common.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {

    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-10-05T03:00:00Z");

    private final TimeSource source;
    private final ZoneId zone;

    private MutableClock(TimeSource source, ZoneId zone) {
        this.source = source;
        this.zone = zone;
    }

    public static MutableClock atDefaultInstant() {
        return at(DEFAULT_INSTANT);
    }

    public static MutableClock at(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return new MutableClock(new TimeSource(instant), ZoneOffset.UTC);
    }

    @Override
    public Instant instant() {
        return source.current.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return new MutableClock(source, zone);
    }

    public void setInstant(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        source.current.set(instant);
    }

    public void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        source.current.updateAndGet(current -> current.plus(duration));
    }

    public void reset() {
        source.current.set(source.initial);
    }

    @Override
    public String toString() {
        return "MutableClock[" + instant() + ", " + zone + "]";
    }

    private static final class TimeSource {

        private final Instant initial;
        private final AtomicReference<Instant> current;

        private TimeSource(Instant initial) {
            this.initial = initial;
            this.current = new AtomicReference<>(initial);
        }
    }
}
