package com.moewadeul.community.application.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommunityViewCountBufferTest {

    private static final long POST_ID = 7L;

    @Test
    @DisplayName("[CM-11] 더한 조회 수를 글별로 꺼내고, 꺼낸 뒤에는 비어 있다")
    void drainReturnsViewsPerPostAndEmptiesBuffer() {
        CommunityViewCountBuffer buffer = new CommunityViewCountBuffer();

        assertThat(buffer.add(POST_ID, 1)).isEqualTo(1);
        assertThat(buffer.add(POST_ID, 1)).isEqualTo(2);
        buffer.add(8L, 3);

        assertThat(buffer.drain()).containsOnly(Map.entry(POST_ID, 2L), Map.entry(8L, 3L));
        assertThat(buffer.drain()).isEmpty();
        assertThat(buffer.add(POST_ID, 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("[CM-11] 여러 스레드가 같은 글에 조회를 더하는 동안 계속 꺼내도, 꺼낸 수의 합은 더한 수와 같다")
    void drainingWhileAddingLosesNoViews() throws Exception {
        CommunityViewCountBuffer buffer = new CommunityViewCountBuffer();
        int threads = 8;
        int addsPerThread = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean adding = new AtomicBoolean(true);
        try {
            List<Future<?>> adders = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                adders.add(executor.submit(() -> {
                    start.await();
                    for (int n = 0; n < addsPerThread; n++) {
                        buffer.add(POST_ID, 1);
                    }
                    return null;
                }));
            }
            Future<Long> drainer = executor.submit(() -> {
                start.await();
                long drained = 0;
                while (adding.get()) {
                    drained += buffer.drain().getOrDefault(POST_ID, 0L);
                }
                return drained;
            });

            start.countDown();
            for (Future<?> adder : adders) {
                adder.get(30, TimeUnit.SECONDS);
            }
            adding.set(false);
            long total = drainer.get(30, TimeUnit.SECONDS) + buffer.drain().getOrDefault(POST_ID, 0L);

            assertThat(total).isEqualTo((long) threads * addsPerThread);
        } finally {
            executor.shutdownNow();
        }
    }
}
