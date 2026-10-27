package com.moewadeul.common.testsupport;

import java.util.concurrent.atomic.AtomicLong;

public final class TestSequence {

    private static final AtomicLong COUNTER = new AtomicLong();

    private TestSequence() {}

    public static long next() {
        return COUNTER.incrementAndGet();
    }

    public static String email() {
        return "user-" + next() + "@example.com";
    }

    public static String nickname() {
        return "nick-" + next();
    }

    public static String unique(String prefix) {
        return prefix + "-" + next();
    }
}
