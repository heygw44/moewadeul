package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolledSnapshotTest {

    private final MutableClock clock = MutableClock.atDefaultInstant();
    private final AtomicInteger reads = new AtomicInteger();
    private Supplier<Integer> reader = reads::incrementAndGet;
    private final PolledSnapshot<Integer> snapshot = new PolledSnapshot<>(() -> reader.get(), clock);

    @Test
    @DisplayName("[NFR-09] 아직 읽은 적이 없으면 0이 아니라 NaN을 돌려준다")
    void isNaNBeforeFirstRefresh() {
        assertThat(snapshot.read(Integer::doubleValue)).isNaN();
    }

    @Test
    @DisplayName("[NFR-09] 값을 읽는 일은 DB 읽기를 부르지 않고, 읽기는 refresh할 때만 일어난다")
    void readingDoesNotInvokeReader() {
        snapshot.refresh();

        snapshot.read(Integer::doubleValue);
        snapshot.read(Integer::doubleValue);

        assertThat(reads).hasValue(1);
        snapshot.refresh();
        assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("[NFR-09] 읽기에 실패해도 STALE_AFTER 안에서는 마지막 값을 돌려주고, 넘으면 NaN으로 바꾼다")
    void staleValueBecomesNaNAfterLimit() {
        snapshot.refresh();
        reader = () -> {
            throw new IllegalStateException("커넥션을 얻지 못했다");
        };

        clock.advance(PolledSnapshot.STALE_AFTER);
        assertThatThrownBy(snapshot::refresh).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot.read(Integer::doubleValue)).isEqualTo(1.0);

        clock.advance(Duration.ofMillis(1));
        assertThat(snapshot.read(Integer::doubleValue)).isNaN();
    }

    @Test
    @DisplayName("[NFR-09] 낡아서 NaN이 된 뒤에도 다음 refresh가 성공하면 다시 값을 돌려준다")
    void recoversAfterSuccessfulRefresh() {
        snapshot.refresh();
        clock.advance(PolledSnapshot.STALE_AFTER.plusSeconds(1));
        assertThat(snapshot.read(Integer::doubleValue)).isNaN();

        snapshot.refresh();

        assertThat(snapshot.read(Integer::doubleValue)).isEqualTo(2.0);
    }
}
