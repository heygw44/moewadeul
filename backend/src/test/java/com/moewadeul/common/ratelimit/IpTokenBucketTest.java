package com.moewadeul.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IpTokenBucketTest {

    private static final String IP = "203.0.113.7";

    private final MutableClock clock = MutableClock.atDefaultInstant();
    private final IpTokenBucket bucket = new IpTokenBucket(60, Duration.ofMinutes(1), clock);

    @Test
    @DisplayName("토큰을 다 쓰면 1초 뒤를 알리며 막고, 1초가 지나면 한 번 더 받는다")
    void refillsOneTokenPerInterval() {
        for (int i = 0; i < 60; i++) {
            assertThat(bucket.tryTake(IP)).isEmpty();
        }

        assertThat(bucket.tryTake(IP)).contains(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(bucket.tryTake(IP)).isEmpty();
        assertThat(bucket.tryTake(IP)).isPresent();
    }

    @Test
    @DisplayName("다른 IP의 토큰은 따로 센다")
    void countsEachIpSeparately() {
        for (int i = 0; i < 61; i++) {
            bucket.tryTake(IP);
        }

        assertThat(bucket.tryTake("203.0.113.8")).isEmpty();
    }
}
